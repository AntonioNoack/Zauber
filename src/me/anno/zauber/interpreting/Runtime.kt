package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.NullType

class Runtime {

    private val classes = HashMap<Type, ZClass>()
    private val nullInstance = Instance(getClass(NullType), emptyArray())

    val callStack = ArrayList<Call>()

    operator fun get(field: SimpleField): Instance {
        return callStack.last().fields[field]
            ?: throw IllegalStateException("Missing field $field")
    }

    operator fun get(instance: Instance, field: Field): Instance {
        val clazz = instance.type
        val fieldIndex = clazz.properties.indexOf(field)
        check(fieldIndex != -1) { "Instance $instance does not have field $field" }
        return instance.properties[fieldIndex]!!
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
        check(fieldIndex != -1) { "Instance $clazz does not have field $field" }
        instance.properties[fieldIndex] = value
    }

    fun isNull(va: Instance) = va == getNull()

    fun getBool(bool: Boolean): Instance {
        TODO()
    }

    fun getNull(): Instance = nullInstance

    fun getClass(selfType: Type): ZClass {
        return classes.getOrPut(selfType) {
            ZClass(selfType)
        }
    }

    fun executeCall(
        self: Instance, method: Constructor,
        valueParameters: List<SimpleField>
    ): Instance {
        TODO()
    }

    fun executeCall(
        self: Instance, method: Method,
        valueParameters: List<SimpleField>
    ): Instance {
        val valueParameters = valueParameters.map { this[it] }

        val body = method.body ?: throw IllegalStateException("Missing body for method $method")
        var simpleBody = method.simpleBody
        if (simpleBody == null) {
            val context = ResolutionContext(method.scope, method.selfType, true, method.returnType)
            simpleBody = ASTSimplifier.simplify(context, body).startBlock
            method.simpleBody = simpleBody
        }
        val call = Call(self)
        val params = method.valueParameters
        for (i in params.indices) {
            val param = params[i]
            call.fields[param.simpleField!!] = valueParameters[i]
        }
        callStack.add(call)
        //try {
        executeBlock(simpleBody)
        //}catch (e: Exception) {}
        return call.returnValue ?: getUnit()
    }

    fun createNumber(base: NumberExpression): Instance {
        val type = base.resolvedType ?: base.resolvedType0
        return when (type) {
            IntType -> {
                val instance = Instance(getClass(IntType), emptyArray())
                instance.rawValue = base.value.toInt()
                instance
            }
            LongType -> {
                val instance = Instance(getClass(IntType), emptyArray())
                instance.rawValue = base.value.toInt()
                instance
            }
            FloatType -> {
                val instance = Instance(getClass(IntType), emptyArray())
                instance.rawValue = base.value.toInt()
                instance
            }
            DoubleType -> {
                val instance = Instance(getClass(IntType), emptyArray())
                instance.rawValue = base.value.toInt()
                instance
            }
            else -> throw NotImplementedError("Create instance of type $type")
        }
    }

    fun createString(value: String): Instance {
        TODO()
    }

    fun getThis(): Instance {
        return callStack.last().self
    }

    fun getUnit(): Instance {
        return this[getNull(), UnitType.clazz.objectField!!]
    }

    fun executeBlock(body: SimpleBlock) {
        // todo push scope
        // todo push fields
        val instructions = body.instructions
        for (i in instructions.indices) {
            val instr = instructions[i]
            println("Executing $instr")
            instr.execute(this)
        }
    }

    fun returnFromCall(value: Instance) {
        TODO()
    }

    fun castToBool(instance: Instance): Boolean {
        val isTrue = instance == getBool(true)
        val isFalse = instance == getBool(false)
        check(isTrue || isFalse) { "Expected value to be either true or false, got $instance" }
        return isTrue
    }

    fun castToInt(value: Instance): Int {
        TODO()
    }

    fun gotoOtherBlock(target: SimpleBlock) {
        TODO("Not yet implemented")
    }

    fun getObjectInstance(selfType: Type?): Instance {
        if (selfType == null) return getUnit()
        TODO()
    }

}