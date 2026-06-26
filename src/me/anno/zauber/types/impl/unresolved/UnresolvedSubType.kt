package me.anno.zauber.types.impl.unresolved

import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType

class UnresolvedSubType(
    val base: Type,
    val className: String,
    val scope: Scope,
    val imports: List<Import>,
    val typeParams: List<Type>?,
) : Type() {

    companion object {
        private val LOGGER = LogManager.getLogger(UnresolvedSubType::class)
    }

    override fun toStringImpl(depth: Int): String {
        return "$base.$className${typeParams?.joinToString(", ", "<", ">") ?: "<?>"}"
    }

    fun getParameterList(): ParameterList {
        clazz[ScopeInitType.AFTER_DISCOVERY]

        val scopeType = clazz.scopeType
        if (!clazz.hasTypeParameters && scopeType?.needsTypeParams() != true) {
            if (scopeType == null) LOGGER.warn("Missing scopeType for $this, assuming no-type-params")
            clazz.setEmptyTypeParams()
        }

        check(clazz.hasTypeParameters) { "Missing type-params for $this ($scopeType) to take typeWithArgs" }
        val baseTypeParams = (resolvedBase.typeParameters ?: emptyParameterList())
        val newTypeParams = ParameterList(
            clazz.typeParameters,
            clazz.typeParameters.map { GenericType(it.scope, it.name) })
        return baseTypeParams + newTypeParams
    }

    private val resolvedBase: ClassType
        get() = base.resolvedName as ClassType

    private val clazz by lazy {
        resolvedBase.clazz.getOrPut(className, null)
    }

    override val resolvedName: ClassType by lazy {
        ClassType(clazz, getParameterList())
    }

    override fun resolveImpl(selfScope: Scope?): Type = resolvedName

    fun withTypeParams(typeArgs: List<Type>?) = UnresolvedSubType(base, className, scope, imports, typeArgs)
}