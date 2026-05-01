package me.anno.zauber.interpreting

import me.anno.utils.CollectionUtils.getOrPutRecursive
import me.anno.utils.CollectionUtils.mapArray
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.expression.SimpleCallable
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.unresolved.UnresolvedType
import me.anno.zauber.types.specialization.MethodSpecialization
import javax.lang.model.type.UnionType

class Runtime {

    companion object {
        private val LOGGER = LogManager.getLogger(Runtime::class)

        val runtime by threadLocal { Runtime() }
    }

    private var instanceCounter = 0
    fun nextInstanceId() = instanceCounter++

    private val classes = HashMap<Type, ZClass>()
    private val nullInstance = Instance(getClass(NullType), emptyArray(), nextInstanceId())

    val callStack = ArrayList<Call>()
    val externalMethods = HashMap<ExternalKey, ExternalMethod>()
    val printed = ArrayList<String>()

    fun register(scope: Scope, name: String, types: List<Type>, method: ExternalMethod) {
        val key = ExternalKey(scope, name, types)
        externalMethods[key] = method
    }

    private fun getMergedField(field: SimpleField): SimpleField {
        var field = field
        while (true) {
            field = field.mergeInfo?.dst
                ?: return field
        }
    }

    operator fun get(field: SimpleField): Instance {
        val currCall = callStack.last()
        return this[currCall, field]
    }

    operator fun get(call: Call, field: SimpleField): Instance {
        val field = getMergedField(field)
        LOGGER.info("Getting SimpleField $field")
        return call.simpleFields[field]
            ?: throw IllegalStateException("Missing field $field, fields: ${call.simpleFields.entries.joinToString("") { (k, v) -> "\n  $k=$v" }}")
    }

    operator fun set(field: SimpleField, value: Instance) {
        // todo a field may be used for both use-cases...
        var field = field
        while (true) {
            field = field.mergeInfo?.dst ?: break
        }

        // todo only if that field really belongs into this scope:
        //  it might belong to one of the scopes before us
        val valueHasValidType = isSubTypeOf(
            field.type,
            value.clazz.type,
            emptyList(),
            ParameterList.emptyParameterList(),
            InsertMode.READ_ONLY
        )
        if (false) check(valueHasValidType) {
            "Assignment of $value into $field invalid, type-mismatch"
        }
        // get(field) // sanity check for existence
        callStack.last().simpleFields[field] = value
    }

    operator fun set(instance: Instance, field: Field, value: Instance) {
        val clazz = instance.clazz
        val fieldIndex = clazz.fields.indexOf(field)
        check(fieldIndex != -1) {
            "Instance $clazz does not have field $field, " +
                    "available: ${clazz.fields.map { it.name }}, " +
                    "fields: ${(clazz.type as ClassType).clazz.fields.map { it.name }}"
        }
        instance.fields[fieldIndex] = value
    }

    fun isNull(va: Instance) = va == getNull()

    fun getBool(bool: Boolean): Instance {
        val boolCompanion = Types.Boolean.clazz[ScopeInitType.AFTER_DISCOVERY].companionObject
            ?: throw IllegalStateException("Missing definition for enum class Boolean")
        val boolInstance = getObjectInstance(boolCompanion.typeWithArgs)
        val fields = boolInstance.clazz.fields
        val name = if (bool) "TRUE" else "FALSE"
        val field = fields.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Missing enum Boolean.$name")
        return boolInstance[field]
    }

    fun getNull(): Instance = nullInstance

    fun getClass(selfType: Type): ZClass {
        return classes.getOrPut(selfType) { ZClass(selfType) }
    }

    fun executeCall(
        self: Instance,
        methodSpec: MethodSpecialization,
        valueParameters: List<SimpleField>
    ): BlockReturn {

        // println("Calling $methodSpec on $self with $valueParameters")

        if (isNull(self)) {
            throw IllegalArgumentException("Cannot execute $methodSpec on null instance")
        }

        val valueParameters = valueParameters.map { valueField ->
            this[valueField].cloneIfValue()
        }

        val method = methodSpec.method
        method.scope[ScopeInitType.CODE_GENERATION] // load method

        if (method.isExternal()) {
            val name = (method as Method).name
            val parameterTypes = method.valueParameters.map { parameter ->
                val type = parameter.type
                (type as? UnresolvedType)?.resolvedName ?: type
            }
            val key = ExternalKey(method.scope.parent!!, name, parameterTypes)
            val method = externalMethods[key]
                ?: throw IllegalStateException("Missing external method $key")
            val value = method.process(self, valueParameters)
            return BlockReturn(ReturnType.RETURN, value)
        }

        if (method.body == null) {
            throw IllegalStateException("Missing body for method $method in ${method.scope.pathStr}")
        }

        val graph = ASTSimplifier.simplify(methodSpec)

        val call = Call(method)
        prepareCall(graph, call, method, self, valueParameters)

        val result = executeBlock(graph.startBlock)

        @Suppress("Since15")
        check(callStack.removeLast() === call)
        // println("Returning $result from call to $method")
        return result ?: BlockReturn(ReturnType.RETURN, getUnit())
    }

    private fun prepareCall(
        graph: SimpleGraph, call: Call,
        method: MethodLike, self: Instance,
        valueParameters: List<Instance>,
    ) {
        call.graph = graph
        callStack.add(call)

        val class0 = getClass(method.scope.typeWithArgs)
        val methodScopeInstance = class0.createInstance()

        assignParameters(method, methodScopeInstance, valueParameters)
        assignThisFields(graph, call, method, self, methodScopeInstance)
        assignCapturedFields(graph, call)
    }

    private fun assignParameters(
        method: MethodLike,
        methodScopeInstance: Instance,
        valueParameters: List<Instance>,
    ) {
        for (i in valueParameters.indices) {
            val parameter = valueParameters[i]
            val field = methodScopeInstance.clazz.fields.getOrNull(i)
                ?: throw IllegalStateException(
                    "Method needs at least as many fields as parameters, " +
                            "$method, " +
                            "fields: ${(methodScopeInstance.clazz.type as ClassType).clazz.fields} -> " +
                            "properties: ${methodScopeInstance.clazz.fields}"
                )
            check(field.name == method.valueParameters[i].name) {
                "Unexpected field order, " +
                        "${methodScopeInstance.clazz.fields.map { it.name }} != ${method.valueParameters.map { it.name }}"
            }
            methodScopeInstance.fields[i] = parameter
        }
    }

    private fun assignThisFields(
        graph: SimpleGraph, call: Call,
        method: MethodLike, self: Instance,
        methodScopeInstance: Instance
    ) {
        for ((selfI, dst) in graph.thisFields) {
            val (scope, isExplicitSelf) = selfI
            call.simpleFields[dst] = when {
                isExplicitSelf -> {
                    check(scope == method.scope)
                    self
                }
                scope.isClassLike() -> {
                    // this check is incorrect for extension methods
                    /*check(scope == method.ownerScope) {
                        "Scope mismatch: $scope != ${method.ownerScope} in $method"
                    }*/
                    self
                }
                scope == method.scope -> methodScopeInstance
                else -> {
                    // not class like -> just create a temporary scope...
                    getClass(scope.typeWithArgs).createInstance()
                }
            }
        }
    }

    private fun assignCapturedFields(graph: SimpleGraph, call: Call) {
        for ((capture, dstField) in graph.capturedFields) {
            val (owner, capturedField) = capture
            val prevCall = findPrevCall(owner)
            val prevCallInstanceRef = prevCall.graph.thisFields[SimpleThis(capturedField.ownerScope, false)]
                ?: throw IllegalStateException("Missing thisField for ${capturedField.ownerScope} in $owner")
            val prevCallInstance = this[prevCall, prevCallInstanceRef]
            call.simpleFields[dstField] = prevCallInstance[capturedField]
        }
    }

    fun findPrevCall(owner: MethodLike): Call {
        for (i in callStack.lastIndex downTo 0) {
            val call = callStack[i]
            if (call.method == owner) return call
        }
        throw IllegalStateException("Missing $owner in callStack")
    }

    fun getUnit(): Instance {
        return getObjectInstance(Types.Unit)
    }

    fun getObjectInstance(type: ClassType): Instance {
        check(type.clazz.isObjectLike() || type.clazz.scopeType == null) {
            "Only objects have an object instance, not ${type.clazz.pathStr})"
        }
        return getClass(type).getOrCreateObjectInstance()
    }

    fun executeBlock(block0: SimpleNode): BlockReturn? {
        var block = block0
        loop@ while (true) {

            val instructions = block.instructions

            if (LOGGER.isDebugEnabled) for (i in instructions.indices) {
                val instr = instructions[i]
                LOGGER.debug("Block[$i] $instr")
            }

            var lastValue: BlockReturn?
            for (i in instructions.indices) {
                val instr = instructions[i]
                if (LOGGER.isDebugEnabled) LOGGER.debug("Executing $instr")
                lastValue = instr.execute()
                if (lastValue != null) {
                    if (lastValue.type == ReturnType.THROW && instr is SimpleCallable) {
                        val handler = instr.onThrown
                        if (handler != null) {
                            this[handler.value] = lastValue.value
                            block = handler.block
                            continue@loop
                        }
                    }

                    if (lastValue.type != ReturnType.VALUE) {
                        when (lastValue.type) {
                            ReturnType.YIELD -> {
                                TODO(
                                    "yield: store all fields in a (new?) lambda instance, " +
                                            "return without handlers"
                                )
                            }
                            // handlers inside the function all were processed already :3
                            ReturnType.RETURN, ReturnType.THROW -> {
                                LOGGER.info("Exited with $lastValue (${instr.javaClass.simpleName}) from ${block0.graph.method}")
                                return lastValue
                            }
                            else -> throw NotImplementedError("Unknown exit type")
                        }
                    }
                }
            }

            // find the next block to execute
            val condition = block.branchCondition
            block = if (condition != null) {
                val conditionI = this[condition].castToBool()
                LOGGER.info("Finished $block, condition: $conditionI -> ${(if (conditionI) block.ifBranch else block.elseBranch)?.blockId}")
                if (conditionI) block.ifBranch else block.elseBranch
            } else {
                LOGGER.info("Finished $block, next: ${block.nextBranch?.blockId}")
                block.nextBranch
            } ?: run {
                LOGGER.info("Exited without return from ${block0.graph.method}")
                return null
            }
        }
    }

    private val typeInstances = HashMap<Type, Instance>()
    fun getTypeInstance(type: Type): Instance {
        return typeInstances.getOrPutRecursive(type, { type ->
            val clazz0 = when (type) {
                is ClassType -> Types.ClassType
                is UnionType -> Types.UnionType
                is GenericType -> Types.GenericType
                else -> Types.TypeT
            }
            val clazz = getClass(clazz0)
            val instance = clazz.createInstance()
            instance.rawValue = type
            instance
        }) { type, instance ->
            when (type) {
                is ClassType -> {
                    val clazz = type.clazz[ScopeInitType.AFTER_DISCOVERY]
                    instance.set("name", clazz.name)
                    if (instance.hasProperty("fields")) {
                        val zType = getClass(type)
                        val fields0 = zType.fields
                        val fieldClass = getClass(Types.Field)
                        val fields1 = fields0.mapArray { field ->
                            val instance = fieldClass.createInstance()
                            instance.set("name", createString(field.name))
                            if (false) {
                                val fieldType = field.resolveValueType(ResolutionContext.minimal)
                                instance.set("type", getTypeInstance(fieldType))
                            }
                            instance
                        }
                        instance.set("fields", fieldClass.createArray(fields1))
                    }
                    // todo set methods, child classes and more...
                }
                is UnionType -> {
                    // todo set members
                }
            }
        }
    }

}