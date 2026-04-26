package me.anno.zauber.ast.simple.expression

import me.anno.utils.LazyMap
import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasAnyFlag
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.FullMap
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.expansion.MethodOverrides.sameParameters
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Instance
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

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
            val selfType = method.scope.parent?.get(ScopeInitType.AFTER_DISCOVERY) ?: return false
            if (!selfType.isClass()) return false // non-class-likes cannot be open
            if (selfType.isObjectLike()) return false // objects cannot be open
            if (selfType.isInterface()) return true // interfaces are always open

            // anything else needs the open flag to be open
            return selfType.flags.hasAnyFlag(Flags.OPEN or Flags.OVERRIDE)
        }

        fun createObjectMap(method: MethodLike): Map<ClassType, MethodLike> {
            // constructors aren't dynamic
            val dynamicDispatch = method is Method && selfTypeIsOpen(method)
            if (!dynamicDispatch) return FullMap(method)

            return LazyMap { invokedType ->
                val selfScope = invokedType.clazz
                if (selfScope == method.scope.parent) {
                    // fast-path
                    method
                } else {
                    val methodTypeParameters = method.typeParameters.map { it.type.resolve(selfScope) }
                    val methodValueParameters = method.valueParameters.map { it.type.resolve(selfScope) }
                    val clazzScope = invokedType.clazz[ScopeInitType.AFTER_DISCOVERY]
                    val choices = clazzScope.methods0.filter { option ->
                        option.name == method.name &&
                                sameParameters(selfScope, option.typeParameters, methodTypeParameters) &&
                                sameParameters(selfScope, option.valueParameters, methodValueParameters)
                    }
                    check(choices.isNotEmpty()) { "Missing $method in $invokedType" }
                    check(choices.size == 1) { "Duplicate $method in $invokedType: $choices" }
                    println(
                        "Selected ${choices.first().scope.pathStr}/${choices.first()} " +
                                "for $invokedType.$method, " +
                                "options: ${clazzScope.methods0.map { it.scope.pathStr }}"
                    )
                    choices.first()
                }
            }
        }
    }

    init {
        if (methodName == "getSize" && methods is FullMap<*, *>) {
            throw IllegalStateException("Testing: methods should not be a fullmap")
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

    constructor(
        dst: SimpleField,
        method: MethodLike,
        methodMap: Map<ClassType, MethodLike>,
        self: SimpleField,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Int
    ) : this(
        dst, (method as? Method)?.name ?: "?", methodMap, method, self, specialization,
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

    override fun eval(): BlockReturn {
        // todo we can have multiple selves... how do we handle that?
        //  class A { fun B.call() {}; init { B().call() } }
        val runtime = runtime
        val self = runtime[self]
        if (runtime.isNull(self)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }

        val method = methods[self.clazz.type as ClassType] ?: sample

        initializeArrayIfNeeded(self, method)

        val method1 = MethodSpecialization(method, specialization)
        return runtime.executeCall(self, method1, valueParameters).retToVal()
    }

    fun initializeArrayIfNeeded(self: Instance, method: MethodLike) {
        val selfType = self.clazz.type.resolvedName
        if (selfType is ClassType && selfType.clazz.pathStr == "zauber.Array" &&
            method is Constructor && valueParameters.size == 1 &&
            method.valueParameters[0].type.resolvedName == Types.Int
        ) {
            val sizeParam = valueParameters[0]
            val size = runtime[sizeParam].castToInt()
            self.rawValue = createArray(selfType.typeParameters?.get(0)?.resolvedName, size)
        }
    }

    fun createArray(type: Type?, size: Int): Any {
        return when (type) {
            Types.Boolean -> BooleanArray(size)
            Types.Byte -> ByteArray(size)
            Types.Short -> ShortArray(size)
            Types.Int -> IntArray(size)
            Types.Long -> LongArray(size)
            Types.Float -> FloatArray(size)
            Types.Double -> DoubleArray(size)
            else -> {
                val nullValue = runtime.getNull()
                Array(size) { nullValue }
            }
        }
    }
}