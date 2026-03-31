package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleInstruction
import me.anno.zauber.ast.simple.SimpleNode
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleCallable
import me.anno.zauber.ast.simple.expression.SimpleGetField
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.TypeResolution.typeToScope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnresolvedType
import me.anno.zauber.types.specialization.MethodSpecialization

class Runtime {

    companion object {
        private val LOGGER = LogManager.getLogger(Runtime::class)
    }

    private var instanceCounter = 0
    fun nextInstanceId() = instanceCounter++

    private val classes = HashMap<Type, ZClass>()
    private val nullInstance = Instance(getClass(NullType), emptyArray(), nextInstanceId())

    val callStack = ArrayList<Call>()
    val thisStack = ArrayList<This>()
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

    operator fun get(field: SimpleField, hint: SimpleInstruction? = null): Instance {
        val field = getMergedField(field)
        val selfScope = field.scopeIfIsThis
        println("getting $field, selfScope: $selfScope")
        return if (selfScope != null) getSelf(field, selfScope, hint) else {
            val currCall = callStack.last()
            currCall.simpleFields[field]
                ?: throw IllegalStateException("Missing field $field, fields: ${currCall.simpleFields}")
        }
    }

    private fun getSelf(field: SimpleField, selfScope: Scope, hint: SimpleInstruction?): Instance {
        val self = thisStack.last()
        var selfScope = selfScope
        if (selfScope.selfAsMethod?.explicitSelfType == true) {
            // "this" inside methods with self-type is ambiguous :(
            //  could actually be one of two things:
            //  - method scope
            //  - receiver type
            // not: outer type (has diff scope)

            check(self.scope == selfScope) { "Expected scope to match..., ${self.scope} vs $selfScope" }
            println("fieldType for self: ${field.type} (${field.type.javaClass.simpleName}, ${(field.type as? ClassType)?.clazz?.scopeType})")

            val fieldBelongsToMethod = if (field.type is ClassType) {
                if (field.type.clazz.isMethodType()) true
                else if (field.type.clazz.isClassLike()) false
                else TODO("self[$selfScope] is unclear: is it the method, or the receiver type? ${selfScope.selfAsMethod!!.selfType}, hint: $hint")
            } else when (hint) {
                // check if field belongs to a method...
                is SimpleGetField -> hint.field.scope.isMethodType()
                is SimpleCall -> {
                    TODO("self[$selfScope] is unclear: is it the method, or the receiver type? ${selfScope.selfAsMethod!!.selfType}, hint: $hint")
                }
                else -> {
                    TODO("self[$selfScope] is unclear: is it the method, or the receiver type? ${selfScope.selfAsMethod!!.selfType}, hint: $hint")
                }
            }
            selfScope = if (fieldBelongsToMethod) {
                selfScope
            } else {
                typeToScope(selfScope.selfAsMethod!!.selfType!!)!!
            }
            println("fieldType for self -> $selfScope, self.scope: ${self.scope}, self.instance: ${self.instance}")
        }
        return when {
            self.scope == selfScope -> self.instance
            selfScope.isObjectLike() -> getObjectInstance(selfScope.typeWithoutArgs)
            else -> getSelfFromCallstack(selfScope)
        }
    }

    private fun getSelfFromCallstack(selfScope: Scope): Instance {
        return callStack.last().scopes.getOrPut(selfScope) {
            check(!selfScope.isClassLike()) {
                "Creating instance for $selfScope, should be defined already! Options: ${callStack.last().scopes}"
            }
            val type = selfScope.typeWithoutArgs
            getClass(type).createInstance()
        }
    }

    operator fun get(instance: Instance, field: Field): Instance {
        val clazz = instance.type
        val fieldIndex = clazz.properties.indexOf(field)
        // println("Getting $instance.$field, $fieldIndex")
        /*if (fieldIndex == -1) {
            val parameter = field.byParameter
            if (parameter is Parameter) {
                val vp = callStack.last().valueParameters
                check(parameter.index in vp.indices) {
                    "Expected parameter #${parameter.index} but got only ${vp.size} total"
                }
                return vp[parameter.index]
            }
        }*/
        check(fieldIndex != -1) { "Instance $instance does not have field $field (${field.scope})" }
        if (fieldIndex >= instance.properties.size)
            throw IllegalStateException("Outdated instance? $instance")
        if (instance.properties[fieldIndex] == null &&
            clazz.type == StringType &&
            field.name == "content"
        ) {
            createStringContentArray(instance, fieldIndex)
        }
        return instance.properties[fieldIndex]
            ?: throw IllegalStateException("$instance.$field[$fieldIndex] accessed before initialization")
    }

    private fun createStringContentArray(instance: Instance, fieldIndex: Int) {
        TODO("Create string content array for $instance")
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
            value.type.type,
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
        val clazz = instance.type
        val fieldIndex = clazz.properties.indexOf(field)
        check(fieldIndex != -1) {
            "Instance $clazz does not have field $field, " +
                    "available: ${clazz.properties.map { it.name }}, " +
                    "fields: ${(clazz.type as ClassType).clazz.fields.map { it.name }}"
        }
        instance.properties[fieldIndex] = value
    }

    fun isNull(va: Instance) = va == getNull()

    fun getBool(bool: Boolean): Instance {
        val boolCompanion = BooleanType.clazz.companionObject
            ?: throw IllegalStateException("Missing definition for enum class Boolean")
        val boolInstance = getObjectInstance(boolCompanion.typeWithoutArgs)
        val fields = boolInstance.type.properties
        val name = if (bool) "TRUE" else "FALSE"
        val field = fields.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Missing enum Boolean.$name")
        return this[boolInstance, field]
    }

    fun getNull(): Instance = nullInstance

    fun getClass(selfType: Type): ZClass {
        return classes.getOrPut(selfType) {
            ZClass(selfType, this)
        }
    }

    fun executeCall(
        self: Instance, method1: MethodSpecialization,
        valueParameters: List<SimpleField>,
        hint: SimpleInstruction? = null
    ): BlockReturn {

        if (isNull(self)) {
            throw IllegalArgumentException("Cannot execute $method1 on null instance")
        }

        val valueParameters = valueParameters.map { this[it, hint] }
        val method = method1.method
        if (method.isExternal()) {
            val name = (method as Method).name
            val parameterTypes = method.valueParameters.map { parameter ->
                val type = parameter.type
                (type as? UnresolvedType)?.resolvedName ?: type
            }
            val key = ExternalKey(method.scope.parent!!, name, parameterTypes)
            val method = externalMethods[key]
                ?: throw IllegalStateException("Missing external method $key")
            val value = method.process(this, self, valueParameters)
            return BlockReturn(ReturnType.RETURN, value)
        }

        if (method.body == null) {
            throw IllegalStateException("Missing body for method $method")
        }

        val simpleBody = ASTSimplifier.simplify(method1)

        val call = Call(self)
        callStack.add(call)

        val methodScopeInstance = getClass(method.scope.typeWithoutArgs).createInstance()
        call.scopes[method.scope] = methodScopeInstance
        for (i in valueParameters.indices) {
            val parameter = valueParameters[i]
            val field = methodScopeInstance.type.properties.getOrNull(i)
                ?: throw IllegalStateException(
                    "Method needs at least as many fields as parameters, " +
                            "$method, " +
                            "fields: ${(methodScopeInstance.type.type as ClassType).clazz.fields} -> " +
                            "properties: ${methodScopeInstance.type.properties}"
                )
            check(field.name == method.valueParameters[i].name) {
                "Field order not as expected, expected parameters to come first"
            }
            methodScopeInstance.properties[i] = parameter
        }

        val oldThisStack = ArrayList(thisStack)
        thisStack.clear()

        val thisScope = if (method.explicitSelfType) method.scope else method.scope.parent!!
        thisStack.add(This(self, thisScope))

        val result = try {
            executeBlock(simpleBody.startBlock)
        } finally {
            thisStack.clear()
            thisStack.addAll(oldThisStack)
        }

        @Suppress("Since15")
        check(callStack.removeLast() === call)
        // println("Returning $result from call to $method")
        return result ?: BlockReturn(ReturnType.RETURN, getUnit())
    }

    fun getThis(): Instance {
        return callStack.last().self
    }

    fun getUnit(): Instance {
        return getObjectInstance(UnitType)
    }

    fun getObjectInstance(type: ClassType): Instance {
        check(type.clazz.isObjectLike() || type.clazz.scopeType == null) {
            "Only objects have an object instance, not ${type.clazz.pathStr})"
        }
        return getClass(type).getOrCreateObjectInstance(this)
    }

    fun executeBlock(block0: SimpleNode): BlockReturn? {
        var block = block0
        loop@ while (true) {

            val tss = thisStack.size
            val instructions = block.instructions

            if (LOGGER.isDebugEnabled) for (i in instructions.indices) {
                val instr = instructions[i]
                LOGGER.debug("Block[$i] $instr")
            }

            try {
                var lastValue: BlockReturn?
                for (i in instructions.indices) {
                    val instr = instructions[i]
                    if (LOGGER.isDebugEnabled) LOGGER.debug("Executing $instr")
                    lastValue = instr.execute(this)
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
                                    println("Exited with $lastValue (${instr.javaClass.simpleName}) from ${block0.graph.method}")
                                    return lastValue
                                }
                                else -> throw NotImplementedError("Unknown exit type")
                            }
                        }
                    }
                }
            } finally {
                while (thisStack.size > tss) {
                    @Suppress("Since15")
                    thisStack.removeLast()
                }
            }

            // find the next block to execute
            val condition = block.branchCondition
            block = if (condition != null) {
                val conditionI = this[condition]
                val conditionJ = castToBool(conditionI)
                println("Finished $block, condition: $conditionJ -> ${(if (conditionJ) block.ifBranch else block.elseBranch)?.blockId}")
                if (conditionJ) block.ifBranch else block.elseBranch
            } else {
                println("Finished $block, next: ${block.nextBranch?.blockId}")
                block.nextBranch
            } ?: run {
                println("Exited without return from ${block0.graph.method}")
                return null
            }
        }
    }

}