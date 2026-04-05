package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.expansion.OverriddenMethods.sameParameters
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.interpreting.RuntimeCast.castToInt
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import me.anno.zauber.utils.LazyMap

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    // todo key should also contain specialization, or should it?
    //  method then could be the specialized one...
    val methods: Map<ClassType, MethodLike>,
    val sample: MethodLike,
    val self: SimpleField,
    specialization: Specialization,
    val typeParameters: List<SimpleField>,
    val valueParameters: List<SimpleField>,
    val scopeBridgingParameters: List<SimpleField>,
    scope: Scope, origin: Int
) : SimpleCallable(dst, specialization, scope, origin) {

    companion object {

        fun selfTypeIsOpen(method: Method): Boolean {
            val selfType = method.scope.parent?.scope ?: return false
            if (!selfType.isClassType()) return false // non-class-likes cannot be open
            if (selfType.isObjectLike()) return false // objects cannot be open
            if (selfType.isInterface()) return true // interfaces are always open

            // anything else needs the open flag to be open
            return selfType.flags.hasFlag(Flags.OPEN)
        }

        fun createObjectMap(method: MethodLike): Map<ClassType, MethodLike> {
            val dynamicDispatch = method is Method &&
                    (method.flags.hasFlag(Flags.OPEN) || method.flags.hasFlag(Flags.OVERRIDE)) &&
                    selfTypeIsOpen(method)
            if (!dynamicDispatch) return FullMap(method)

            return LazyMap { invokedType ->
                val selfScope = invokedType.clazz
                val methodTypeParameters = method.typeParameters.map { it.type.resolve(selfScope) }
                val methodValueParameters = method.valueParameters.map { it.type.resolve(selfScope) }
                val choices = invokedType.clazz.scope.methods.filter { option ->
                    option.name == method.name &&
                            sameParameters(selfScope, option.typeParameters, methodTypeParameters) &&
                            sameParameters(selfScope, option.valueParameters, methodValueParameters)
                }
                check(choices.isNotEmpty()) { "Missing $method in $invokedType" }
                check(choices.size == 1) { "Duplicate $method in $invokedType: $choices" }
                choices.first()
            }
        }
    }

    constructor(
        dst: SimpleField,
        method: MethodLike,
        self: SimpleField,
        specialization: Specialization,
        typeParameters: List<SimpleField>,
        valueParameters: List<SimpleField>,
        scopeBridgingParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, (method as? Method)?.name ?: "?", createObjectMap(method), method, self,
        specialization, typeParameters, valueParameters, scopeBridgingParameters, scope, origin
    )

    constructor(
        dst: SimpleField,
        method: MethodLike,
        self: SimpleField,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, method, self, specialization,
        emptyList(), valueParameters, emptyList(),
        scope, origin
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
        // todo we can have multiple selves... how do we handle that?
        //  class A { fun B.call() {}; init { B().call() } }
        val self = runtime[self, this]
        if (runtime.isNull(self)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }

        val method = methods[self.type.type as ClassType] ?: sample

        initializeArrayIfNeeded(self, method, runtime)

        val method1 = MethodSpecialization(method, specialization)
        val result = runtime.executeCall(self, method1, valueParameters, this)
        return if (result.type == ReturnType.RETURN) {
            BlockReturn(ReturnType.VALUE, result.value)
        } else result
    }

    fun initializeArrayIfNeeded(self: Instance, method: MethodLike, runtime: Runtime) {
        val selfType = self.type.type.resolvedName
        if (selfType is ClassType && selfType.clazz.pathStr == "zauber.Array" &&
            method is Constructor && valueParameters.size == 1 &&
            method.valueParameters[0].type.resolvedName == IntType
        ) {
            val sizeParam = valueParameters[0]
            val size = runtime.castToInt(runtime[sizeParam])
            self.rawValue = createArray(selfType.typeParameters?.get(0)?.resolvedName, size)
        }
    }

    fun createArray(type: Type?, size: Int): Any {
        return when (type) {
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