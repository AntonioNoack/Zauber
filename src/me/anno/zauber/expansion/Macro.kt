package me.anno.zauber.expansion

import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.ZauberASTBuilderBase
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.unresolved.CallExpression
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression.Companion.nameExpression
import me.anno.zauber.ast.simple.Ownership
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.generation.Specializations
import me.anno.zauber.interpreting.*
import me.anno.zauber.interpreting.RuntimeCreate.createString
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.scope.lazy.TokenSubList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.typeresolution.members.ResolvedMember
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.specialization.MethodSpecialization

object Macro {

    val macroContentParam = Types.StringType
    val macroContextParam = Types.MacroContextType

    fun ZauberASTBuilderBase.evaluateMacro(namePath: String, typeParameters: List<Type>?, origin: Int): Expression {

        val scope = currPackage
        val context = ResolutionContext(
            null, Specializations.specialization, true,
            null, emptyMap()
        )

        val valueParameters = listOf(
            ValueParameterImpl(null, macroContentParam, false),
            ValueParameterImpl(null, macroContextParam, false),
        )

        val byMethodCall = MethodResolver.resolveCallable(
            context, scope,
            namePath, imports.filter { it.name == namePath },
            constructor = null, typeParameters, valueParameters, origin
        )

        if (byMethodCall == null || byMethodCall.resolved !is Method) {
            val base = nameExpression(namePath, origin, scope)
            val tmpExpr = CallExpression(base, typeParameters, emptyList(), origin + 1)
            MethodResolver.printScopeForMissingMethod(context, tmpExpr, namePath, typeParameters, valueParameters)
        }

        val i0 = i - 1 // 'i0' is on name, 'i' is on virtual '('
        skipCall() // 'i' is now after call

        val content = tokens.extractString(i0 + 2, i - 1)

        val method = byMethodCall.resolved
        val result = executeMacroInRuntime(method, byMethodCall, content)
        val tokenList = extractTokensFromRuntime(result, method, i0)

        return LazyExpression.eval(
            tokenList, 0, tokenList.size, false, scope,
            imports, genericParams.last()
        )
    }

    fun executeMacroInRuntime(method: Method, byMethodCall: ResolvedMember<*>, content: String): BlockReturn {
        val runtime = Runtime.runtime
        val ownerScope = method.scope.parent
        check(ownerScope != null && ownerScope.isObjectLike())
        val owner = runtime.getObjectInstance(ownerScope.typeWithArgs)
        val method1 = MethodSpecialization(method, byMethodCall.specialization)
        val field0 = SimpleField(macroContentParam, Ownership.SHARED, 0)
        val field1 = SimpleField(macroContextParam, Ownership.SHARED, 1)

        val callForFields = Call(runtime.getNull())
        runtime.callStack.add(callForFields)

        val tokenInfoInstance = runtime.getObjectInstance(macroContextParam)
        runtime[field0] = runtime.createString(content)
        runtime[field1] = tokenInfoInstance

        val valueParameters = listOf(field0, field1)
        val result = runtime.executeCall(owner, method1, valueParameters, null)

        @Suppress("Since15")
        check(callForFields == runtime.callStack.removeLast())
        return result
    }

    fun ZauberASTBuilderBase.extractTokensFromRuntime(result: BlockReturn, method: Method, i0: Int): TokenList {
        val runtime = Runtime.runtime
        check(result.type == ReturnType.THROW) { "Failed calling $method at ${tokens.err(i0)}" }
        check(result.value.type == runtime.getClass(Types.MacroContextType))

        val resultIndex = result.value.type.properties.indexOfFirst { it.name == "result" }
        val value = result.value.properties.getOrNull(resultIndex)
            ?: throw IllegalStateException("Missing first property of TokenResult")

        val rawTokens = value.rawValue
        check(rawTokens is Array<*>) { "Expected TokenResult[0] to be Array<String>" }

        val tokenSource = StringBuilder()
        val pseudoFilename = "${method.name}@${tokens.err(i0)}"
        val tokenList = TokenList(tokenSource, pseudoFilename)
        for (i in rawTokens.indices) {
            val asString = (rawTokens[i] as Instance).castToString()
            if (asString.isBlank()) continue

            val type = TokenType.findTokenType(asString)
            val i0 = tokenSource.length
            tokenSource.append(asString).append(' ') // space is not strictly necessary
            if (asString.first() in "\"'") {
                tokenList.add(type, i0 + 1, i0 + asString.length - 1)
            } else {
                tokenList.add(type, i0, i0 + asString.length)
            }
        }

        return tokenList
    }

}