package me.anno.zauber.ast.simple.expression

import me.anno.utils.FullMap
import me.anno.utils.LazyMap
import me.anno.utils.StringStyles
import me.anno.utils.assertEquals
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasAnyFlag
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.expansion.MethodOverrides.sameParameters
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.impl.ClassType

class SimpleCall(
    dst: SimpleField,
    // for complex types, e.g. Float|String, we may need multiple methods, or some sort of instance-type->method mapping
    val methodName: String,
    // todo key should also contain specialization, or should it?
    //  method then could be the specialized one...
    val methods: Map<ClassType, MethodLike>,
    thisInstance: SimpleField,
    val selfInstance: SimpleField?,
    specialization: Specialization,
    val typeParameters: List<SimpleField>,
    val valueParameters: List<SimpleField>,
    val scopeBridgingParameters: List<SimpleField>,
    scope: Scope, origin: Long
) : SimpleCallable(dst, thisInstance, specialization, scope, origin) {

    companion object {

        fun selfTypeIsOpen(method: Method): Boolean {
            val selfType = method.ownerScope

            selfType[ScopeInitType.AFTER_DISCOVERY]

            if (!selfType.isClass()) return false // non-class-likes cannot be open
            if (selfType.isObjectLike()) return false // objects cannot be open
            if (selfType.isInterface()) return true // interfaces are always open

            when (method.scope.scopeType) {
                ScopeType.FIELD_GETTER,
                ScopeType.FIELD_SETTER -> {
                    val field = method.backedField!!
                    if (!field.isOpen()) return false
                }
                else -> {}
            }

            // anything else needs the open flag to be open
            return selfType.flags.hasAnyFlag(Flags.OPEN or Flags.OVERRIDE)
        }

        fun createDynamicDispatchMap(method: MethodLike): Map<ClassType, MethodLike> {

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
                    if (false) println(
                        "Selected ${choices.first().scope.pathStr}/${choices.first()} " +
                                "for $invokedType.$method, " +
                                "options: ${clazzScope.methods0.map { it.scope.pathStr }}"
                    )
                    choices.first()
                }
            }
        }
    }

    constructor(
        dst: SimpleField,
        method: MethodLike,
        thisInstance: SimpleField,
        selfInstance: SimpleField?,
        specialization: Specialization,
        typeParameters: List<SimpleField>,
        valueParameters: List<SimpleField>,
        scopeBridgingParameters: List<SimpleField>,
        scope: Scope, origin: Long
    ) : this(
        dst, (method as? Method)?.name ?: "?", createDynamicDispatchMap(method),
        thisInstance, selfInstance, specialization,
        typeParameters, valueParameters, scopeBridgingParameters, scope, origin
    )

    constructor(
        dst: SimpleField,
        method: MethodLike,
        thisInstance: SimpleField,
        selfInstance: SimpleField?,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Long
    ) : this(
        dst, method, thisInstance, selfInstance, specialization,
        emptyList(), valueParameters, emptyList(),
        scope, origin
    )

    constructor(
        dst: SimpleField,
        method: MethodLike,
        methodMap: Map<ClassType, MethodLike>,
        thisInstance: SimpleField,
        selfInstance: SimpleField?,
        specialization: Specialization,
        valueParameters: List<SimpleField>,
        scope: Scope, origin: Long
    ) : this(
        dst, (method as? Method)?.name ?: "?", methodMap,
        thisInstance, selfInstance, specialization,
        emptyList(), valueParameters, emptyList(),
        scope, origin
    )

    init {
        for (method in methods.values) {
            check(method.valueParameters.size == valueParameters.size)
        }
        assertEquals(sample.hasExplicitSelfType, selfInstance != null) {
            "Method $sample mismatches explicit-selfInstance $selfInstance"
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
            .append(thisInstance).append('[').append(sample.selfType).append("].")
            .append(StringStyles.style(methodName, StringStyles.GREEN))
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
        val thisInstance = runtime[thisInstance]
        if (runtime.isNull(thisInstance)) {
            // this should never happen
            throw IllegalStateException("Unexpected NPE: $this")
        }

        val selfInstance =
            if (selfInstance != null) runtime[selfInstance]
            else null

        println("Running $sample with $thisInstance/$selfInstance")

        val method = methods[thisInstance.clazz.type as ClassType] ?: sample
        val method1 = Specialization(method.memberScope, specialization.typeParameters)
        return runtime.executeCall(thisInstance, selfInstance, method1, valueParameters).retToVal()
    }
}