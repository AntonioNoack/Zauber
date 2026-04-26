package me.anno.zauber.typeresolution.members

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.typeresolution.ValueParameter
import me.anno.zauber.typeresolution.members.FieldResolver.resolveField
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeCallPart
import me.anno.zauber.typeresolution.members.MergeTypeParams.mergeTypeParameters
import me.anno.zauber.typeresolution.members.ResolvedMethod.Companion.selfTypeToTypeParams
import me.anno.zauber.types.Import
import me.anno.zauber.types.Type

object MethodResolver : MemberResolver<Method, ResolvedMethod>() {

    private val LOGGER = LogManager.getLogger(MethodResolver::class)

    override fun findMemberInScope(
        scope: Scope?, origin: Int, name: String,

        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        context: ResolutionContext
    ): ResolvedMethod? {
        scope ?: return null

        val sit1 = ScopeInitType.AFTER_OVERRIDES
        val sit2 = ScopeInitType.AFTER_DISCOVERY

        val scopeSelfType = getSelfType(scope)
        val children = scope[sit1].children
        var bestMatch: ResolvedMethod? = null
        for (i in children.indices) {

            val child = children[i][sit2]
            val method = child.selfAsMethod ?: continue
            if (method.name != name) continue

            if (LOGGER.isInfoEnabled && method.typeParameters.isNotEmpty()) {
                LOGGER.info("Given $method in $context, can we deduct any generics from that?")
            }

            val returnType = context.targetType

            val methodReturnType0 = if (returnType != null) {
                getMethodReturnType(scopeSelfType, method)
            } else method.returnType // no resolution invoked (fast-path)
            val methodReturnType1 = methodReturnType0?.specialize(context)
            val selfType = context.selfType?.specialize(context)
            if (LOGGER.isInfoEnabled) LOGGER.info("MethodReturnType: $methodReturnType1 -> $returnType, selfType: $selfType")
            val match = findMemberMatch(
                method, methodReturnType1, returnType,
                selfType, typeParameters, valueParameters,
                /* todo is this fine??? */scope, origin
            )
            if (match != null && (bestMatch == null || match.matchScore < bestMatch.matchScore)) {
                bestMatch = match
            }
        }
        return bestMatch
    }

    fun getMethodReturnType(scopeSelfType: Type?, method: Method): Type? {
        if (method.returnType == null) {
            val selfType = method.selfType?.resolvedName ?: scopeSelfType
            if (LOGGER.isInfoEnabled) LOGGER.info("Resolving ${method.scope}.type by ${method.body}, selfType: $selfType")
            val context = ResolutionContext(selfType, false, null, emptyMap())
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
        actualValueParameters: List<ValueParameter>,
        codeScope: Scope, origin: Int
    ): ResolvedMethod? {
        val avp = actualValueParameters.size
        val mvp = method.valueParameters.size
        if (if (method.hasVarargParameter) avp + 1 < mvp else avp < mvp) {
            LOGGER.info("Rejecting $method, not enough value parameters")
            return null
        }
        if (avp > mvp && !method.hasVarargParameter) {
            LOGGER.info("Rejecting $method, too many value parameters")
            return null
        }

        val matchScore = MatchScore(method.valueParameters.size + 2)
        val methodSelfType = method.selfType?.resolvedName
        return if (methodSelfType == null) {

            val actualTypeParameters = mergeCallPart(method.typeParameters, typeParameters, origin)
            if (LOGGER.isInfoEnabled) LOGGER.info("Merged ${method.typeParameters} with $typeParameters into $actualTypeParameters")

            if (LOGGER.isInfoEnabled) LOGGER.info("Resolving generics for $method")
            val generics = findGenericsForMatch(
                null, null,
                methodReturnType, returnType,
                method.typeParameters, actualTypeParameters,
                method.valueParameters, actualValueParameters, matchScore
            ) ?: return null

            val context = ResolutionContext(null, false, returnType, emptyMap())
            ResolvedMethod(
                emptyParameterList(), method, generics,
                context, codeScope, matchScore
            )
        } else {

            val methodSelfParams = selfTypeToTypeParams(method.selfType, selfType)
            val actualTypeParams = mergeTypeParameters(
                methodSelfParams, selfType,
                method.typeParameters, typeParameters, origin
            )

            if (LOGGER.isInfoEnabled) LOGGER.info("Resolving generics for $method")
            // println("Resolving generics for $method")
            val generics = findGenericsForMatch(
                methodSelfType, selfType,
                methodReturnType, returnType,
                methodSelfParams + method.typeParameters, actualTypeParams,
                method.valueParameters, actualValueParameters, matchScore
            ) ?: return null

            val selfType1 = selfType ?: methodSelfType
            val context = ResolutionContext(selfType1, false, returnType, emptyMap())
            // println("selfType: $selfType1, generics: $generics for $method")
            ResolvedMethod(
                generics.subList(0, methodSelfParams.size), method,
                generics.subList(methodSelfParams.size, generics.size),
                context, codeScope, matchScore
            )
        }
    }

    fun resolveCallable(
        context: ResolutionContext, codeScope: Scope,
        name: String, nameAsImport: List<Import>,
        constructor: ResolvedMember<*>?,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMember<*>? {
        return constructor
            ?: resolveMethod(
                context, codeScope,
                name, nameAsImport,
                typeParameters, valueParameters, origin
            )
            // todo value-parameters should be considered for fun-interfaces/LambdaTypes
            ?: resolveField(
                context, codeScope,
                name, nameAsImport,
                typeParameters, origin
            )
    }

    fun printScopeForMissingMethod(
        context: ResolutionContext, expr: Expression, name: String,
        typeParameters: List<Type>?, valueParameters: List<ValueParameter>
    ): Nothing {
        val selfScope = context.selfScope
        val codeScope = expr.scope
        LOGGER.warn("Self-scope methods[${selfScope?.pathStr}.'$name']: ${selfScope?.methods0?.filter { it.name == name }}")
        LOGGER.warn("Code-scope methods[${codeScope.pathStr}.'$name']: ${codeScope.methods0.filter { it.name == name }}")
        LOGGER.warn("Lang-scope methods[${langScope.pathStr}.'$name']: ${langScope.methods0.filter { it.name == name }}")
        throw IllegalStateException(
            "Could not resolve method ${selfScope?.pathStr}.'$name'<$typeParameters>($valueParameters) " +
                    "in ${resolveOrigin(expr.origin)}, scopes: ${codeScope.pathStr}"
        )
    }

    fun resolveMethod(
        context: ResolutionContext, codeScope: Scope,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMember<*>? {
        return resolveInCodeScope(context, codeScope) { scope, selfType ->
            findMemberInScope(
                scope, origin, name, context.targetType,
                selfType, typeParameters, valueParameters, context
            )
        } ?: resolveByImports(
            context, name, nameAsImport,
            typeParameters, valueParameters, origin
        )
    }

    fun resolveByImports(
        context: ResolutionContext,
        name: String, nameAsImport: List<Import>,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMember<*>? {
        for (import in nameAsImport) {
            if (import.name != name) continue
            val resolved = resolveByImport(
                context, import.path,
                typeParameters, valueParameters, origin
            )
            if (resolved != null) return resolved
        }
        return null
    }

    fun resolveByImport(
        context: ResolutionContext,
        nameAsImport: Scope,
        typeParameters: List<Type>?,
        valueParameters: List<ValueParameter>,
        origin: Int
    ): ResolvedMember<*>? {

        val methodOwner = nameAsImport.parent
        if (methodOwner != null) {
            val methodSelfType = if (methodOwner.isObject())
                methodOwner.typeWithArgs else context.selfType
            val importedMethod = findMemberInScope(
                methodOwner, origin, nameAsImport.name, context.targetType, methodSelfType,
                typeParameters, valueParameters, context
            )
            if (importedMethod != null) return importedMethod

            val ownerCompanion = methodOwner.companionObject
            if (ownerCompanion != null) {
                val companionSelfType = ownerCompanion.typeWithArgs
                val importedCompanionMethod = findMemberInScope(
                    ownerCompanion, origin, nameAsImport.name, context.targetType, companionSelfType,
                    typeParameters, valueParameters, context
                )
                if (importedCompanionMethod != null) return importedCompanionMethod
            }
        }

        val importedConstructor = ConstructorResolver.findMemberInScopeImpl(
            nameAsImport, nameAsImport.name,
            typeParameters, valueParameters, context
        )
        if (importedConstructor != null) return importedConstructor

        return null
    }

    fun null1(): ResolvedField? {
        return null
    }
}