package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type

object MethodResolver : MemberResolver<Method, ResolvedMethod>() {

    private val LOGGER = LogManager.getLogger(MethodResolver::class)

    override fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>
    ): ResolvedMethod? {
        scope ?: return null
        val scopeSelfType = getSelfType(scope)
        val children = scope.children
        for (i in children.indices) {
            val method = children[i].selfAsMethod ?: continue
            if (method.name != name) continue
            if (method.typeParameters.isNotEmpty()) {
                LOGGER.info("Given $method on $selfType, with target $returnType, can we deduct any generics from that?")
            }
            val methodReturnType = if (returnType != null) {
                getMethodReturnType(scopeSelfType, method)
            } else method.returnType // no resolution invoked (fast-path)
            val match = findMemberMatch(
                method, methodReturnType, returnType,
                selfType, typeParameters, valueParameters,
                origin
            )
            if (match != null) return match
        }
        return null
    }

    fun getMethodReturnType(scopeSelfType: Type?, method: Method): Type? {
        if (method.returnType == null) {
            if (false) LOGGER.info("Resolving ${method.scope}.type by ${method.body}")
            val context = ResolutionContext(
                method.scope,
                method.selfType ?: scopeSelfType,
                false, null
            )
            method.returnType = method.resolveReturnType(context)
        }
        return method.returnType
    }

    fun findMemberMatch(
        method: Method,
        methodReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMethod? {

        val methodSelfParams = selfTypeToTypeParams(method.selfType, selfType)
        val actualTypeParams = mergeTypeParameters(
            methodSelfParams, selfType,
            method.typeParameters, typeParameters,
            origin
        )

        LOGGER.info("Resolving generics for $method")
        val generics = findGenericsForMatch(
            method.selfType, if (method.selfType == null) null else selfType,
            methodReturnType, returnType,
            methodSelfParams + method.typeParameters, actualTypeParams,
            method.valueParameters, valueParameters
        ) ?: return null

        val selfType = selfType ?: method.selfType
        val context = ResolutionContext(method.scope, selfType, false, returnType)
        return ResolvedMethod(
            generics.subList(0, methodSelfParams.size), method,
            generics.subList(methodSelfParams.size, generics.size), context
        )
    }

    fun resolveCallable(
        context: ResolutionContext,
        name: String,
        constructor: ResolvedMember<*>?,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMember<*>? {
        val method = constructor ?: resolveMethod(context, name, typeParameters, valueParameters, origin)
        if (method != null) return method

        return resolveField(context, name, typeParameters, origin)
    }

    fun printScopeForMissingMethod(
        context: ResolutionContext, expr: Expression, name: String,
        typeParameters: List<Type>?, valueParameters: List<ValueParameter>
    ): Nothing {
        val selfScope = context.selfScope
        val codeScope = context.codeScope
        LOGGER.warn("Self-scope methods[${selfScope?.pathStr}.'$name']: ${selfScope?.methods?.filter { it.name == name }}")
        LOGGER.warn("Code-scope methods[${codeScope.pathStr}.'$name']: ${codeScope.methods.filter { it.name == name }}")
        LOGGER.warn("Lang-scope methods[${langScope.pathStr}.'$name']: ${langScope.methods.filter { it.name == name }}")
        throw IllegalStateException(
            "Could not resolve method ${selfScope?.pathStr}.'$name'<$typeParameters>($valueParameters) " +
                    "in ${resolveOrigin(expr.origin)}, scopes: ${codeScope.pathStr}"
        )
    }

    fun resolveMethod(
        context: ResolutionContext,
        name: String,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMethod? {
        return resolveInCodeScope(context) { scope, selfType ->
            findMemberInHierarchy(
                scope, origin, name, context.targetType,
                selfType, typeParameters, valueParameters
            )
        }
    }

    fun null1(): ResolvedField? {
        return null
    }
}