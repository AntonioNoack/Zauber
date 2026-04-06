package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.NamedParameter
import me.anno.zauber.ast.rich.ZauberASTBuilderBase
import me.anno.zauber.ast.rich.expression.DynamicMacroExpression
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression.Companion.nameExpression
import me.anno.zauber.ast.simple.Ownership
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.generation.Specializations
import me.anno.zauber.interpreting.*
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.typeresolution.members.ResolvedMethod
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.specialization.MethodSpecialization

object Macro {

    val macroContentParam = Types.String
    val macroContextParam = Types.MacroContext

    fun ZauberASTBuilderBase.evaluateMacro(
        namePath: Expression, typeParameters: List<Type>?,
        valueParameters: List<NamedParameter>, origin: Int
    ): Expression {
        TODO("Resolve macro $namePath")
    }

    fun ZauberASTBuilderBase.evaluateMacro(namePath: String, typeParameters: List<Type>?, origin: Int): Expression {

        val i0 = i - 1 // 'i0' is on name, 'i' is on virtual '('
        skipCall() // 'i' is now after call

        val content = tokens.extractString(i0 + 2, i - 1)
        println("Evaluating macro '$content' using $namePath")
        val valueParameters = listOf(Runtime.runtime.createString(content))
        return evaluateMacroNow(namePath, typeParameters, valueParameters, origin, i0)
    }

    private fun createContext(): ResolutionContext {
        return ResolutionContext(
            null, Specializations.specialization, true,
            null, emptyMap()
        )
    }

    private fun ZauberASTBuilderBase.resolveMacroByName(
        namePath: String, typeParameters: List<Type>?,
        valueParametersTypes: List<Type>, origin: Int
    ): ResolvedMethod {
        val scope = currPackage
        val context = createContext()

        val valueParameters1 = valueParametersTypes.map { type ->
            ValueParameterImpl(null, type, false)
        } + ValueParameterImpl(null, macroContextParam, false)

        val byMethodCall = MethodResolver.resolveCallable(
            context, scope,
            namePath, imports.filter { it.name == namePath },
            constructor = null, typeParameters, valueParameters1, origin
        )

        if (byMethodCall == null || byMethodCall.resolved !is Method) {
            val base = nameExpression(namePath, origin, scope)
            val tmpExpr = CallExpression(base, typeParameters, emptyList(), origin + 1)
            MethodResolver.printScopeForMissingMethod(context, tmpExpr, namePath, typeParameters, valueParameters1)
        }

        check(byMethodCall.resolved.flags.hasFlag(Flags.MACRO)) {
            "Expected to resolve to macro, got $byMethodCall"
        }

        return byMethodCall as ResolvedMethod
    }

    private fun codeIsInsideAMacro(currPackage: Scope): Boolean {
        var scope = currPackage
        while (true) {
            val method = scope.selfAsMethod
            if (method != null && method.flags.hasFlag(Flags.MACRO)) {
                return true
            }

            scope = scope.parentIfSameFile ?: return false
        }
    }

    private fun getObjectScope(currPackage: Scope): Scope {
        var scope = currPackage
        while (true) {
            if (scope.isObjectLike()) return scope
            if (scope.isClassLike()) {
                throw IllegalStateException("Expected objectLike scope for macro-execution in $currPackage, not $scope")
            }

            scope = scope.parentIfSameFile
                ?: throw IllegalStateException("Missing objectLike scope for macro-execution in $currPackage")
        }
    }

    fun ZauberASTBuilderBase.evaluateMacro(
        namePath: String,
        typeParameters: List<Type>?,
        valueParameters: List<NamedParameter>,
        origin: Int, i0: Int
    ): Expression {
        val context = createContext()
        val valueParameterTypes = valueParameters.map { it.value.resolveReturnType(context) }
        if (codeIsInsideAMacro(currPackage)) {
            val macro = resolveMacroByName(namePath, typeParameters, valueParameterTypes, origin)
            return DynamicMacroExpression(macro, valueParameters, currPackage, origin)
        } else {
            val runtime = Runtime.runtime
            val instance = runtime.getObjectInstance(getObjectScope(currPackage).typeWithArgs)
            val valueParameters = valueParameters.mapIndexed { index, parameter ->
                runtime.evaluateExpression(instance, parameter.value, Flags.NONE, valueParameterTypes[index])
            }
            return evaluateMacroNow(namePath, typeParameters, valueParameters, origin, i0)
        }
    }

    fun ZauberASTBuilderBase.evaluateMacroNow(
        namePath: String,
        typeParameters: List<Type>?,
        valueParameters: List<Instance>,
        origin: Int, i0: Int
    ): Expression {
        check(namePath != "GetType") {
            "GetType is called with proper quotes, so how???"
        }

        val valueParameterTypes = valueParameters.map { it.clazz.type }
        val macro = resolveMacroByName(namePath, typeParameters, valueParameterTypes, origin)

        val method = macro.resolved
        val result = executeMacroInRuntime(method, macro, valueParameters)
        val tokenList = extractTokensFromRuntime(result, method, i0)

        return LazyExpression.eval(
            tokenList, 0, tokenList.size, isBody = true, currPackage,
            imports, genericParams.last()
        )
    }

    fun executeMacroInRuntime(
        method: Method,
        byMethodCall: ResolvedMember<*>,
        valueParameters: List<Instance>
    ): BlockReturn {
        val runtime = Runtime.runtime
        val ownerScope = method.scope.parent
        check(ownerScope != null && ownerScope.isObjectLike())
        val owner = runtime.getObjectInstance(ownerScope.typeWithArgs)
        val method1 = MethodSpecialization(method, byMethodCall.specialization)

        val callForFields = Call(runtime.getNull())
        runtime.callStack.add(callForFields)

        val valueParameters1 = valueParameters + runtime.getObjectInstance(macroContextParam)
        val valueParameters2 = valueParameters1.mapIndexed { index, instance ->
            SimpleField(instance.clazz.type, Ownership.SHARED, index)
        }

        for (i in valueParameters2.indices) {
            runtime[valueParameters2[i]] = valueParameters1[i]
        }

        val result = runtime.executeCall(owner, method1, valueParameters2, null)

        @Suppress("Since15")
        check(callForFields == runtime.callStack.removeLast())
        return result
    }

    fun ZauberASTBuilderBase.extractTokensFromRuntime(result: BlockReturn, method: Method, i0: Int): TokenList {
        val runtime = Runtime.runtime
        check(result.type == ReturnType.THROW) { "Failed calling $method at ${tokens.err(i0)}" }
        check(result.value.clazz == runtime.getClass(Types.MacroContext))

        val resultIndex = result.value.clazz.properties.indexOfFirst { it.name == "result" }
        val value = result.value.properties.getOrNull(resultIndex)
            ?: throw IllegalStateException("Missing first property of TokenResult")

        // todo allow special character codes to encode where something came from...
        val tokenSource = value.castToString()
        val pseudoFilename = "${method.name}@${tokens.errShort(i0)}"
        return ZauberTokenizer(tokenSource, pseudoFilename)
            .tokenize()
    }

}