package me.anno.zauber.typeresolution

import me.anno.zauber.astbuilder.NamedParameter
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.types.Type

/**
 * A value parameter that depends on what it's used for.
 * */
class UnderdefinedValueParameter(
    val param: NamedParameter,
    val context: ResolutionContext,
) : ValueParameter(param.name) {

    companion object {
        private val LOGGER = LogManager.getLogger(UnderdefinedValueParameter::class)
    }

    override fun getType(targetType: Type): Type {
        val expr = param.value
        LOGGER.info("Expr for resolving lambda/generics: $expr, tt: $targetType")
        // this is targetType-specific, so we should clone expr
        return resolveType(context.withTargetType(targetType), expr.clone(expr.scope))
    }

    override fun toString(): String {
        return "UnderdefinedValueParameter(name=$name,value=${param.value},context=$context)"
    }
}