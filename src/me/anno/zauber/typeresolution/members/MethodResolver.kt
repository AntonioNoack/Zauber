package me.anno.zauber.typeresolution.members

import me.anno.zauber.astbuilder.Method
import me.anno.zauber.astbuilder.TokenListIndex.resolveOrigin
import me.anno.zauber.astbuilder.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.TypeResolution.resolveType
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

object MethodResolver : MemberResolver<Method, ResolvedMethod>() {

    private val LOGGER = LogManager.getLogger(MethodResolver::class)

    override fun findMemberInScope(
        scope: Scope?, name: String,

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
                selfType, typeParameters, valueParameters
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
            method.returnType = resolveType(context, method.body!!)
        }
        return method.returnType
    }

    fun findMemberMatch(
        method: Method,
        methodReturnType: Type?,

        returnType: Type?, // sometimes, we know what to expect from the return type
        selfType: Type?, // if inside Companion/Object/Class/Interface, this is defined; else null

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>
    ): ResolvedMethod? {

        val methodSelfParams = selfTypeToTypeParams(method.selfType)
        val actualTypeParams = mergeTypeParameters(
            methodSelfParams, selfType,
            method.typeParameters, typeParameters,
        )

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

    fun resolveCallType(
        context: ResolutionContext,
        expr: Expression,
        name: String,
        constructor: ResolvedCallable<*>?,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
    ): Type {
        val targetType = context.targetType
        val selfType = context.selfType
        val m = MethodResolver
        val method = constructor
            ?: m.findMemberInHierarchy(context.selfScope, name, targetType, selfType, typeParameters, valueParameters)
            ?: m.findMemberInFile(context.codeScope, name, targetType, selfType, typeParameters, valueParameters)
            ?: m.findMemberInFile(langScope, name, targetType, selfType, typeParameters, valueParameters)
        val f = FieldResolver
        val field = null1()
            ?: f.findMemberInHierarchy(context.selfScope, name, selfType, targetType, typeParameters, valueParameters)
            ?: f.findMemberInFile(context.codeScope, name, selfType, targetType, typeParameters, valueParameters)
            ?: f.findMemberInFile(langScope, name, selfType, targetType, typeParameters, valueParameters)
        val candidates =
            listOfNotNull(method?.getTypeFromCall(), field?.getTypeFromCall())
        if (candidates.isEmpty()) {
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
        if (candidates.size > 1) throw IllegalStateException("Cannot have both a method and a type with the same name '$name': $candidates")
        return candidates.first()
    }

    fun null1(): ResolvedField? {
        return null
    }
}