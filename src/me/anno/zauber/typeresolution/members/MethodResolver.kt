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
import me.anno.zauber.types.Import
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
                /* todo is this fine??? */scope, origin
            )
            if (match != null) return match
        }
        return null
    }

    fun getMethodReturnType(scopeSelfType: Type?, method: Method): Type? {
        if (method.returnType == null) {
            val selfType = method.selfType ?: scopeSelfType
            LOGGER.info("Resolving ${method.scope}.type by ${method.body}, selfType: $selfType")
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
        valueParameters: List<ValueParameter>,
        codeScope: Scope, origin: Int
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
        val context = ResolutionContext(selfType, false, returnType, emptyMap())
        return ResolvedMethod(
            generics.subList(0, methodSelfParams.size), method,
            generics.subList(methodSelfParams.size, generics.size),
            context, codeScope
        )
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
        LOGGER.warn("Self-scope methods[${selfScope?.pathStr}.'$name']: ${selfScope?.methods?.filter { it.name == name }}")
        LOGGER.warn("Code-scope methods[${codeScope.pathStr}.'$name']: ${codeScope.methods.filter { it.name == name }}")
        LOGGER.warn("Lang-scope methods[${langScope.pathStr}.'$name']: ${langScope.methods.filter { it.name == name }}")
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
                selfType, typeParameters, valueParameters
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
                typeParameters, valueParameters
            )
            if (importedMethod != null) return importedMethod

            val ownerCompanion = methodOwner.companionObject
            if (ownerCompanion != null) {
                val companionSelfType = ownerCompanion.typeWithArgs
                val importedCompanionMethod = findMemberInScope(
                    ownerCompanion, origin, nameAsImport.name, context.targetType, companionSelfType,
                    typeParameters, valueParameters
                )
                if (importedCompanionMethod != null) return importedCompanionMethod
            }
        }

        val importedConstructor = ConstructorResolver
            .findMemberInScopeImpl(
                nameAsImport, nameAsImport.name, context.targetType, context.selfType,
                typeParameters, valueParameters
            )
        if (importedConstructor != null) return importedConstructor

        return null
    }

    fun null1(): ResolvedField? {
        return null
    }
}