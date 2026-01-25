package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.NullType

class Runtime {

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
        val selfScope = field.scopeIfIsThis
        if (selfScope != null) {
            // should we check only within the current call? I think so...
            for (i in thisStack.lastIndex downTo 0) {
                val self = thisStack[i]
                if (self.scope == selfScope) {
                    println("Found this@$selfScope: ${self.instance}")
                    return self.instance
                }
            }
            // might be an object...
            println("Returning object instance for $selfScope")
            return getObjectInstance(selfScope.typeWithoutArgs)
        }

        println("Using field in call $field")
        return callStack.last().fields[field]
            ?: throw IllegalStateException("Missing field $field")
    }

    operator fun get(instance: Instance, field: Field): Instance {
        val clazz = instance.type
        val fieldIndex = clazz.properties.indexOf(field)
        println("Getting $instance.$field, $fieldIndex")
        if (fieldIndex == -1) {
            val parameter = field.byParameter
            if (parameter is Parameter) {
                val vp = callStack.last().valueParameters
                check(parameter.index in vp.indices) {
                    "Expected parameter #${parameter.index} but got only ${vp.size} total"
                }
                return vp[parameter.index]
            }
        }
        check(fieldIndex != -1) { "Instance $instance does not have field $field (${field.codeScope})" }
        return instance.properties[fieldIndex]
            ?: throw IllegalStateException("$instance.$field[$fieldIndex] accessed before initialization")
    }

    operator fun set(field: SimpleField, value: Instance) {
        // todo only if that field really belongs into this scope:
        //  it might belong to one of the scopes before us
        // get(field) // sanity check for existence
        callStack.last().fields[field] = value
    }

    operator fun set(instance: Instance, field: Field, value: Instance) {
        val clazz = instance.type
        val fieldIndex = clazz.properties.indexOf(field)
        check(fieldIndex != -1) { "Instance $clazz does not have field $field, available: ${clazz.properties}" }
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
            val context = ResolutionContext(method.selfType, true, method.returnType, emptyMap())
            simpleBody = ASTSimplifier.simplify(context, body).startBlock
            method.simpleBody = simpleBody
        }

        val call = Call(self, valueParameters)
        callStack.add(call)

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

        println("Return value: ${call.returnValue}")
        return result ?: BlockReturn(ReturnType.RETURN, getUnit())
    }

    fun createNumber(base: NumberExpression): Instance {
        val type = base.resolvedType ?: base.resolvedType0
        var value = base.value
        val basis = when {
            value.startsWith("0x", true) -> {
                value = value.substring(2)
                16
            }
            value.startsWith("0b", true) -> {
                value = value.substring(2)
                2
            }
            else -> 10
        }
        return when (type) {
            ByteType -> {
                val instance = Instance(getClass(ByteType), emptyArray())
                instance.rawValue = value.toByte(basis)
                instance
            }
            ShortType -> {
                val instance = Instance(getClass(ShortType), emptyArray())
                instance.rawValue = value.toShort(basis)
                instance
            }
            IntType -> createInt(value.toInt(basis))
            LongType -> createLong(value.toLong(basis))
            FloatType -> {
                val instance = Instance(getClass(FloatType), emptyArray())
                instance.rawValue = base.value.toFloat()
                instance
            }
            DoubleType -> {
                val instance = Instance(getClass(DoubleType), emptyArray())
                instance.rawValue = base.value.toDouble()
                instance
            }
            else -> throw NotImplementedError("Create instance of type $type")
        }
    }

    fun createInt(value: Int): Instance {
        val instance = Instance(getClass(IntType), emptyArray())
        instance.rawValue = value
        return instance
    }

    fun createLong(value: Long): Instance {
        val instance = Instance(getClass(LongType), emptyArray())
        instance.rawValue = value
        return instance
    }

    fun createString(value: String): Instance {
        val instance = Instance(getClass(StringType), emptyArray())
        instance.rawValue = value
        return instance
    }

    fun getThis(): Instance {
        return callStack.last().self
    }

    fun getUnit(): Instance {
        return getObjectInstance(UnitType)
    }

    fun getObjectInstance(type: ClassType): Instance {
        return getClass(type).getOrCreateObjectInstance(this)
    }

    fun executeBlock(body: SimpleBlock): BlockReturn? {
        // todo push fields
        val tss = thisStack.size
        val instructions = body.instructions
        try {
            var lastValue: BlockReturn? = null
            for (i in instructions.indices) {
                val instr = instructions[i]
                println("Executing $instr")
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
        return null
    }

    fun castToBool(instance: Instance): Boolean {
        val isTrue = instance == getBool(true)
        val isFalse = instance == getBool(false)
        check(isTrue || isFalse) { "Expected value to be either true or false, got $instance" }
        return isTrue
    }

    fun castToInt(value: Instance): Int {
        check(value.type == getClass(IntType)) {
            "Casting $value to int failed, type mismatch"
        }
        return value.rawValue as Int
    }

    fun castToString(value: Instance): String {
        check(value.type == getClass(StringType)) {
            "Casting $value to String failed, type mismatch"
        }
        return value.rawValue as String
    }

    fun gotoOtherBlock(target: SimpleBlock) {
        TODO("Not yet implemented")
    }
}