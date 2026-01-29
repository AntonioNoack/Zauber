package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleNode
import me.anno.zauber.interpreting.RuntimeCast.castToBool
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.Inheritance.isSubTypeOf
import me.anno.zauber.typeresolution.InsertMode
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType

class Runtime {

    companion object {
        private val LOGGER = LogManager.getLogger(Runtime::class)
    }

    private val classes = HashMap<Type, ZClass>()
    private val nullInstance = Instance(getClass(NullType), emptyArray())

    val callStack = ArrayList<Call>()
    val thisStack = ArrayList<This>()
    val externalMethods = HashMap<ExternalKey, ExternalMethod>()
    val printed = ArrayList<String>()

    fun register(scope: Scope, name: String, types: List<Type>, method: ExternalMethod) {
        val key = ExternalKey(scope, name, types)
        externalMethods[key] = method
    }

    operator fun get(field: SimpleField): Instance {
        // todo a field may be used for both use-cases...
        var field = field
        while (true) {
            field = field.mergeInfo?.dst ?: break
        }

        val selfScope = field.scopeIfIsThis
        if (selfScope != null) {
            // should we check only within the current call? I think so...
            for (i in thisStack.lastIndex downTo 0) {
                val self = thisStack[i]
                if (self.scope == selfScope) {
                    // println("Found this@$selfScope: ${self.instance}")
                    return self.instance
                }
            }

            if (selfScope.isObject()) {
                // might be an object...
                // println("Returning object instance for $selfScope")
                return getObjectInstance(selfScope.typeWithoutArgs)
            } else if (!selfScope.isObject()) {
                val currCall = callStack.last()
                return currCall.scopes.getOrPut(selfScope) {
                    val type = selfScope.typeWithoutArgs
                    getClass(type).createInstance()
                }
            }
        }

        val currCall = callStack.last()
        return currCall.simpleFields[field]
            ?: throw IllegalStateException("Missing field $field, fields: ${currCall.simpleFields}")
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
        check(fieldIndex != -1) { "Instance $instance does not have field $field (${field.codeScope})" }
        if (fieldIndex >= instance.properties.size)
            throw IllegalStateException("Outdated instance? $instance")
        return instance.properties[fieldIndex]
            ?: throw IllegalStateException("$instance.$field[$fieldIndex] accessed before initialization")
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
        val field = fields.first { it.name == name }
        return this[boolInstance, field]
    }

    fun getNull(): Instance = nullInstance

    fun getClass(selfType: Type): ZClass {
        return classes.getOrPut(selfType) {
            ZClass(selfType)
        }
    }

    fun executeCall(
        self: Instance, method: MethodLike,
        valueParameters: List<SimpleField>
    ): BlockReturn {
        val valueParameters = valueParameters.map { this[it] }
        if (method.isExternal()) {
            val name = (method as Method).name!!
            val key = ExternalKey(method.scope.parent!!, name, method.valueParameters.map { it.type })
            val method = externalMethods[key]
                ?: throw IllegalStateException("Missing external method $key")
            val value = method.process(this, self, valueParameters)
            return BlockReturn(ReturnType.RETURN, value)
        }

        val body = method.body
            ?: throw IllegalStateException("Missing body for method $method")

        var simpleBody = method.simpleBody
        if (simpleBody == null) {
            val context = ResolutionContext(self.type.type, true, method.returnType, emptyMap())
            simpleBody = ASTSimplifier.simplify(context, body).startBlock
            method.simpleBody = simpleBody
        }

        val call = Call(self)
        callStack.add(call)

        val methodScopeInstance = getClass(method.scope.typeWithoutArgs).createInstance()
        call.scopes[method.scope] = methodScopeInstance
        for (i in valueParameters.indices) {
            val parameter = valueParameters[i]
            val field = methodScopeInstance.type.properties[i]
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
            executeBlock(simpleBody)
        } finally {
            thisStack.clear()
            thisStack.addAll(oldThisStack)
        }

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
        check(type.clazz.isObject()) { "Only objects have an object instance, not ${type.clazz.pathStr}" }
        return getClass(type).getOrCreateObjectInstance(this)
    }

    fun executeBlock(block0: SimpleNode): BlockReturn? {
        var block = block0
        while (true) {

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
                    // todo we must execute all (err)defer-things
                    lastValue = instr.execute(this)
                    if (lastValue != null && lastValue.type != ReturnType.VALUE) {
                        return lastValue
                    }
                }
            } finally {
                while (thisStack.size > tss) {
                    thisStack.removeLast()
                }
            }

            // find the next block to execute
            val condition = block.branchCondition
            block = if (condition != null) {
                val conditionI = this[condition]
                val conditionJ = castToBool(conditionI)
                if (conditionJ) block.ifBranch else block.elseBranch
            } else {
                block.nextBranch
            } ?: return null
        }
    }

}