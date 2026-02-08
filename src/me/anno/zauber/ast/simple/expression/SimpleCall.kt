package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.Specialization

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    // todo key should also contain specialization, or should it?
    //  method then could be the specialized one...
    val methods: Map<Type, MethodLike>,
    val sample: MethodLike,
    val self: SimpleField,
    val specialization: Specialization,
    val valueParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleCallable(dst, scope, origin) {

    constructor(
        dst: SimpleField,
        method: MethodLike,
        self: SimpleField,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, (method as? Method)?.name ?: "?", FullMap(method), method, self,
        specialization, valueParameters, scope, origin
    )

    init {
        for (method in methods.values) {
            check(method.valueParameters.size == valueParameters.size)
        }
    }

    // todo these depend on whether all types are instantiable,
    //   and also on available specializations...

    /*val thrownType: Type by lazy {
        val types = methods.values.map { method -> IsMethodThrowing[method] }
        unionTypes(types)
    }

    val yieldedType: Type by lazy {
        val types = methods.values.map { method -> IsMethodYielding[method] }
        unionTypes(types)
    }*/

    override fun toString(): String {
        val builder = StringBuilder()
        builder.append(dst).append(" = ")
            .append(self).append('[').append(sample.selfType).append("].")
            .append(methodName)
            .append(valueParameters.joinToString(", ", "(", ")"))
        val ot = onThrown
        if (ot != null) {
            builder.append(" throws b").append(ot.block.blockId)
                .append("(%").append(ot.value.id).append(')')
        }
        return builder.toString()
    }

    override fun eval(runtime: Runtime): BlockReturn {
        val self = runtime[self]
        val method = methods[self.type.type] ?: sample

        initializeArrayIfNeeded(self, method, runtime)

        val result = runtime.executeCall(self, method, valueParameters)
        return if (result.type == ReturnType.RETURN) {
            BlockReturn(ReturnType.VALUE, result.value)
        } else result
    }

    fun initializeArrayIfNeeded(self: Instance, method: MethodLike, runtime: Runtime) {
        val selfType = self.type.type
        if (selfType is ClassType && selfType.clazz.pathStr == "zauber.Array" &&
            method is Constructor && valueParameters.size == 1 &&
            method.valueParameters[0].type == IntType
        ) {
            val size = runtime.castToInt(runtime[valueParameters[0]])
            self.rawValue = when (selfType.typeParameters?.get(0)) {
                BooleanType -> BooleanArray(size)
                ByteType -> ByteArray(size)
                ShortType -> ShortArray(size)
                IntType -> IntArray(size)
                LongType -> LongArray(size)
                FloatType -> FloatArray(size)
                DoubleType -> DoubleArray(size)
                else -> arrayOfNulls<Instance>(size)
            }
        }
    }

}