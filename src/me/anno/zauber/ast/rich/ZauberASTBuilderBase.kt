package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.java.ast.JavaASTBuilder
import me.anno.support.java.ast.JavaASTClassScanner
import me.anno.support.java.ast.NamedCastExpression
import me.anno.zauber.ast.rich.ZauberASTClassScanner.Companion.resolveTypeAliases
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.binaryOp
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.*
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.NumberType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType.typeOrNull
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes

abstract class ZauberASTBuilderBase(
    tokens: TokenList, root: Scope,
    val allowUnresolvedTypes: Boolean
) : ASTBuilderBase(tokens, root) {

    companion object {
        private val LOGGER = LogManager.getLogger(ZauberASTBuilderBase::class)

        fun resolveUnresolvedTypes(path: Type?): Type? {
            var path = path
            while (true) {
                path = when (path) {
                    is UnresolvedType -> path.resolve()
                    is ClassType if path.clazz.isTypeAlias() -> resolveTypeAliases(path)
                    else -> return path
                }
            }
        }

        fun resolveTypeByName(
            selfType: Type?, name: String,
            currPackage: Scope, imports: List<Import>
        ): Type? {
            return currPackage.resolveTypeOrNull(name, imports, true)
                ?: (selfType as? ClassType)?.clazz?.resolveType(name, imports)
        }
    }

    abstract fun readMethodBody(): ExpressionList
    abstract fun readExpression(minPrecedence: Int = 0): Expression
    abstract fun readBodyOrExpression(label: String?): Expression

    fun readTypeAlias() {
        val newName = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
        val pseudoScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        pseudoScope.typeParameters = readTypeParameterDeclarations(pseudoScope)

        check(tokens.equals(i++, "="))
        val trueType = readType(null, true)
        pseudoScope.selfAsTypeAlias = trueType
        popGenericParams()
    }

    fun readTypeOrNull(selfType: Type?): Type? {
        return if (consumeIf(":")) {
            readType(selfType, true)
                ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
        } else null
    }

    fun readTypeParameterDeclarations(classScope: Scope): List<Parameter> {
        pushGenericParams()
        if (!tokens.equals(i, "<")) return emptyList()
        val parameters = ArrayList<Parameter>()
        tokens.push(i++, "<", ">") {
            while (i < tokens.size) {
                // todo store & use these?
                consumeIf("in")
                consumeIf("out")

                val origin = origin(i)
                val name = consumeName(VSCodeType.TYPE_PARAM, 0)

                // name might be needed for the type, so register it already here
                genericParams.last()[name] = GenericType(classScope, name)

                val type = readTypeOrNull(classScope.typeWithoutArgs) ?: NullableAnyType
                // if you print type here, typeParameters may not be available yet, and cause an NPE

                parameters.add(Parameter(parameters.size, name, type, classScope, origin))
                readComma()
            }
        }
        consume(">")
        classScope.typeParameters = parameters
        classScope.hasTypeParameters = true
        return parameters
    }

    fun readType(selfType: Type?, allowSubTypes: Boolean): Type? {
        return readType(
            selfType, allowSubTypes,
            isAndType = false, insideTypeParams = false
        )
    }

    fun readExpressionCondition(): Expression {
        return pushCall { readExpression() }
    }

    fun readValueParameters(): ArrayList<NamedParameter> {
        return pushCall { readValueParametersImpl() }
    }

    fun readValueParametersImpl(): ArrayList<NamedParameter> {
        val parameters = ArrayList<NamedParameter>()
        while (i < tokens.size) {
            val name = if (
                (this !is JavaASTBuilder && this !is JavaASTClassScanner) &&
                tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, "=")
            ) {
                val name = tokens.toString(i)
                i += 2
                name
            } else null
            val value = readExpression()
            val param = NamedParameter(name, value)
            println("param[${parameters.size}]: $name=$value")
            parameters.add(param)
            if (LOGGER.isDebugEnabled) LOGGER.debug("read param: $param")
            readComma()
        }
        return parameters
    }

    fun readRHS(op: Operator): Expression =
        readExpression(if (op.assoc == Assoc.LEFT) op.precedence + 1 else op.precedence)

    fun handleDotOperator(lhs: Expression): Expression {
        val op = dotOperator
        val rhs = readRHS(op)
        if (rhs is CallExpression && rhs.self is MemberNameExpression) {
            return NamedCallExpression(
                lhs, rhs.self.name, rhs.self.nameAsImport,
                rhs.typeParameters, rhs.valueParameters,
                rhs.scope, rhs.origin
            )
        }
        return binaryOp(currPackage, lhs, op.symbol, rhs)
    }

    fun handleShortcutOperator(
        expr: Expression, symbol: String, op: Operator,
        scope: Scope, origin: Int,
    ): Expression {
        val name = currPackage.generateName("shortcut", origin)
        val right = pushScope(name, ScopeType.METHOD_BODY) { readRHS(op) }
        return if (symbol == "&&") shortcutExpressionI(expr, ShortcutOperator.AND, right, scope, origin)
        else shortcutExpressionI(expr, ShortcutOperator.OR, right, scope, origin)
    }

    fun readIfBranch(): IfElseBranch {
        val origin = origin(i)
        var condition = readExpressionCondition()
        val ifTrue = if (condition is NamedCastExpression) {
            // to do we could use the same scope for field and body...
            val instanceName = condition.newName
            val instanceTest = condition.instanceTest
            condition = condition.instanceTest

            pushScope(ScopeType.METHOD_BODY, "cast") { scopeForField ->
                val dstField = scopeForField.addField(
                    null, false, false, null,
                    instanceName, instanceTest.type,
                    null, Keywords.NONE, origin
                )
                val dstFieldExpr = FieldExpression(dstField, scopeForField, origin)
                val assignment = AssignmentExpression(dstFieldExpr, instanceTest.value)
                val remainder = readBodyOrExpression(null)
                ExpressionList(listOf(assignment, remainder), scopeForField, origin)
            }
        } else readBodyOrExpression(null)
        val ifFalse = if (tokens.equals(i, "else") && !tokens.equals(i + 1, "->")) {
            consume("else")
            pushScope(ScopeType.METHOD_BODY, "else") {
                when {
                    consumeIf("if") -> readIfBranch()
                    consumeIf("for") -> TODO("read for after else")
                    consumeIf("while") -> readWhileLoop(null)
                    consumeIf("do") -> readDoWhileLoop(null)
                    else -> readBodyOrExpression(null)
                }
            }
        } else null
        // println("Scopes: ${condition.scope}, ${ifTrue.scope}, ${ifFalse?.scope}")
        return IfElseBranch(condition, ifTrue, ifFalse)
    }

    fun readWhileLoop(label: String?): WhileLoop {
        val condition = readExpressionCondition()
        val body = readBodyOrExpression(label ?: "")
        return WhileLoop(condition, body, label)
    }

    fun readDoWhileLoop(label: String?): DoWhileLoop {
        val body = readBodyOrExpression(label ?: "")
        check(tokens.equals(i++, "while"))
        val condition = readExpressionCondition()
        return DoWhileLoop(body = body, condition = condition, label)
    }

    abstract fun readParameterDeclarations(selfType: Type?): List<Parameter>

    fun readTryCatch(): TryCatchBlock {
        val tryBody = readBodyOrExpression(null)
        val catches = ArrayList<Catch>()
        while (consumeIf("catch")) {
            val origin = origin(i - 1)
            check(tokens.equals(i, TokenType.OPEN_CALL))
            val catchName = currPackage.generateName("catch", origin)
            pushScope(catchName, ScopeType.METHOD_BODY) {
                val params = pushCall { readParameterDeclarations(null) }
                check(params.size == 1)
                val handler = readBodyOrExpression(null)
                catches.add(Catch(params[0], handler))
            }
        }
        val finally = if (consumeIf("finally")) {
            val origin = origin(i - 1)
            val body = readBodyOrExpression(null)
            val flagName = body.scope.generateName("finallyFlag", origin)
            val flag = body.scope.addField(
                null, false, true,
                null, flagName, BooleanType, null, Keywords.SYNTHETIC, origin
            )
            Finally(body, flag)
        } else null
        return TryCatchBlock(tryBody, catches, finally)
    }

    fun readTypeNotNull(selfType: Type?, allowSubTypes: Boolean): Type {
        return readType(selfType, allowSubTypes)
            ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
    }

    fun readType(
        selfType: Type?, allowSubTypes: Boolean,
        isAndType: Boolean, insideTypeParams: Boolean
    ): Type? {
        val i0 = i
        val negate = consumeIf("!", VSCodeType.OPERATOR, 0)
        if (consumeIf("*", VSCodeType.TYPE, 0)) {
            return UnknownType
        }
        if (this is JavaASTBuilder || this is JavaASTClassScanner) {
            if (consumeIf("?", VSCodeType.TYPE, 0)) {
                return UnknownType
            }
        }

        var base = readTypeExpr(selfType, allowSubTypes, insideTypeParams)
            ?: run {
                i = i0 // undo any reading
                return null
            }

        if (allowSubTypes && consumeIf(".")) {
            base = readType(base, true, isAndType, insideTypeParams)
                ?: throw IllegalStateException("Expected to be able to read subtype")
            return if (negate) base.not() else base
        }

        if (consumeIf("?")) {
            base = typeOrNull(base)
        }

        if (negate) base = base.not()
        while (consumeIf("&", VSCodeType.OPERATOR, 0)) {
            val typeB = readType(null, allowSubTypes, true, insideTypeParams)!!
            base = andTypes(base, typeB)
        }
        if (!isAndType && consumeIf("|", VSCodeType.OPERATOR, 0)) {
            val typeB = readType(null, allowSubTypes, false, insideTypeParams)!!
            return unionTypes(base, typeB)
        }
        return base
    }

    fun <R> pushBlock(scopeType: ScopeType, scopeName: String?, readImpl: (Scope) -> R): R {
        val name = scopeName ?: currPackage.generateName(scopeType.name, origin(i))
        return pushScope(name, scopeType) { scope ->
            readInBlock(scope, readImpl)
        }
    }

    fun <R> pushBlock(scope: Scope, readImpl: (Scope) -> R): R {
        return pushScope(scope) {
            readInBlock(scope, readImpl)
        }
    }

    fun <R> readInBlock(childScope: Scope, readImpl: (Scope) -> R): R {
        val blockEnd = tokens.findBlockEnd(i++, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
        val result = tokens.push(blockEnd) { readImpl(childScope) }
        check(i == blockEnd) {
            "Tokens were skipped, $i != $blockEnd at ${tokens.err(i)}"
        }
        consume(TokenType.CLOSE_BLOCK)
        return result
    }

    fun <R> push(endTokenIdx: Int, readImpl: () -> R): R {
        val result = tokens.push(endTokenIdx, readImpl)
        i = endTokenIdx + 1 // skip }
        return result
    }

    private fun readTypeExpr(selfType: Type?, allowSubTypes: Boolean, insideTypeParams: Boolean): Type? {

        if (consumeIf("*", VSCodeType.TYPE, 0)) {
            return UnknownType
        }

        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            val endI = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
            if (tokens.equals(endI + 1, "->")) {
                val parameters = pushCall { readLambdaParameter() }
                i = endI + 2 // skip ) and ->
                val returnType = readTypeNotNull(selfType, true)
                return LambdaType(null, parameters, returnType)
            } else {
                return pushCall { readType(selfType, true) }
            }
        }

        if (this !is JavaASTBuilder && this !is JavaASTClassScanner) {
            if (tokens.equals(i, TokenType.NUMBER)) {
                if (!insideTypeParams) {
                    throw IllegalStateException("Comptime-Values are only supported in type-params, ${tokens.err(i)}")
                }
                val value = tokens.toString(i++)
                return ComptimeValue(NumberType, listOf(value))
            }

            if (tokens.equals(i, TokenType.STRING)) {
                if (!insideTypeParams) {
                    throw IllegalStateException("Comptime-Values are only supported in type-params, ${tokens.err(i)}")
                }
                val value = tokens.toString(i++)
                return ComptimeValue(StringType, listOf(value))
            }
        }

        val path = readTypePath(selfType) // e.g. ArrayList
            ?: return null

        val typeArgs = readTypeParameters(selfType)
        val baseType = when {
            path is ClassType -> ClassType(path.clazz, typeArgs)
            path is UnresolvedType -> UnresolvedType(path.className, typeArgs, currPackage, imports)
            typeArgs == null -> path
            else -> throw IllegalStateException("Cannot combine $path with $typeArgs")
        }

        if (allowSubTypes && consumeIf(".")) {
            // read lambda/inner subtype
            val childType = readTypeNotNull(selfType, true)
            val joinedType = if (childType is LambdaType && childType.selfType == null) {
                LambdaType(baseType, childType.parameters, childType.returnType)
            } else SubType(baseType, childType)
            return joinedType
        }

        return baseType
    }

    fun readTypeParameters(selfType: Type?): List<Type>? {
        if (i < tokens.size) {
            if (LOGGER.isDebugEnabled) LOGGER.debug(
                "checking for type-args, ${tokens.err(i)}, ${
                    isTypeArgsStartingHere(
                        i
                    )
                }"
            )
        }
        // having type arguments means they no longer need to be resolved
        // todo any method call without them must resolve which ones and how many there are, e.g. mapOf, listOf, ...
        if (!isTypeArgsStartingHere(i)) {
            return null
        }

        i++ // consume '<'

        val args = ArrayList<Type>()
        while (true) {
            // todo store these (?)
            consumeIf("in")
            consumeIf("out")
            val type = readType(selfType, allowSubTypes = true, isAndType = false, insideTypeParams = true)
                ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
            args.add(type) // recursive type
            when {
                consumeIf(",") -> {}
                consumeIf(">") -> break
                else -> throw IllegalStateException("Expected , or > in type arguments, got ${tokens.err(i)}")
            }
        }
        return args
    }

    fun readLambdaParameter(): List<LambdaParameter> {
        val result = ArrayList<LambdaParameter>()
        loop@ while (i < tokens.size) {
            if (tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, ":")
            // && tokens.equals(i + 2, TokenType.NAME)
            ) {
                val name = tokens.toString(i)
                i += 2
                val type = readTypeNotNull(null, true)
                result.add(LambdaParameter(name, type))
            } else if (tokens.equals(i, TokenType.NAME)) {
                val type = readTypeNotNull(null, true)
                result.add(LambdaParameter(null, type))
            } else throw IllegalStateException("Expected name: Type or name at ${tokens.err(i)}")
            readComma()
        }
        return result
    }

    /**
     * check whether only valid symbols appear here
     * check whether brackets make sense
     *    for now, there is only ( and )
     * */
    private fun isTypeArgsStartingHere(i: Int): Boolean {
        if (i >= tokens.size) return false
        if (!tokens.equals(i, "<")) return false
        if (!tokens.isSameLine(i - 1, i)) return false
        var depth = 1
        var i = i + 1
        while (depth > 0) {
            if (i >= tokens.size) return false // reached end without closing the block
            if (LOGGER.isDebugEnabled) LOGGER.debug("  check ${tokens.err(i)} for type-args-compatibility")
            // todo support annotations here?
            when {
                tokens.equals(i, TokenType.COMMA) -> {} // ok
                tokens.equals(i, TokenType.NAME) -> {} // ok
                tokens.equals(i, TokenType.STRING) ||
                        tokens.equals(i, TokenType.NUMBER) -> {
                } // comptime values
                tokens.equals(i, "<") -> depth++
                tokens.equals(i, ">") -> depth--
                tokens.equals(i, "?") ||
                        tokens.equals(i, "->") ||
                        tokens.equals(i, ":") || // names are allowed
                        tokens.equals(i, ".") ||
                        tokens.equals(i, "in") ||
                        tokens.equals(i, "out") ||
                        tokens.equals(i, "*") -> {
                    // ok
                }
                tokens.equals(i, TokenType.OPEN_CALL) -> depth++
                tokens.equals(i, TokenType.CLOSE_CALL) -> depth--
                tokens.equals(i, TokenType.OPEN_BLOCK) ||
                        tokens.equals(i, TokenType.CLOSE_BLOCK) ||
                        tokens.equals(i, TokenType.OPEN_ARRAY) ||
                        tokens.equals(i, TokenType.CLOSE_ARRAY) ||
                        tokens.equals(i, TokenType.SYMBOL) ||
                        tokens.equals(i, TokenType.APPEND_STRING) ||
                        tokens.equals(i, "val", "var") ||
                        tokens.equals(i, "else") ||
                        tokens.equals(i, "fun", "override") ||
                        tokens.equals(i, "this") -> return false
                else -> throw NotImplementedError("Can ${tokens.err(i)} appear inside a type?")
            }
            i++
        }
        return true
    }

    private fun resolveSelfTypeI(selfType: Type?): Type {
        if (selfType is ClassType) {
            return selfType
        } else {
            check(selfType == null)
            var scope = currPackage
            while (!scope.isClassType()) {
                scope = scope.parent
                    ?: throw IllegalStateException("Could not resolve Self-type in $currPackage at ${tokens.err(i - 1)}")
            }
            return scope.typeWithoutArgs
        }
    }

    /**
     * ClassType | SelfType
     * */
    fun readTypePath(selfType: Type?): Type? {
        if (!tokens.equals(i, TokenType.NAME)) return null

        val name = tokens.toString(i++)
        if (this is ZauberASTBuilder) {
            setLSType(i - 1, VSCodeType.TYPE, 0)
        }

        if (this !is JavaASTBuilder && this !is JavaASTClassScanner) {
            when (name) {
                "Self" -> return SelfType((resolveSelfTypeI(selfType) as ClassType).clazz)
                "This" -> return ThisType(resolveSelfTypeI(selfType))
            }
        } else {
            when (name) {
                "byte" -> return ByteType
                "short" -> return ShortType
                "char" -> return CharType
                "int" -> return IntType
                "long" -> return LongType
                "float" -> return FloatType
                "double" -> return DoubleType
                "boolean" -> return BooleanType
                "void" -> return UnitType
            }
        }

        var path: Type? = genericParams.last()[name]
        path = path ?: if (allowUnresolvedTypes) {
            UnresolvedType(name, null, currPackage, imports)
        } else {
            resolveTypeByName(selfType, name, currPackage, imports)
        }

        if (!allowUnresolvedTypes) {
            path = resolveUnresolvedTypes(path)
        }

        if (path == null) {
            i--
            LOGGER.warn("Unresolved type '$name' in $currPackage/$selfType at ${tokens.err(i)}")
            return null
        }

        // todo support deeper unresolved types??
        while (tokens.equals(i, ".") && tokens.equals(i + 1, TokenType.NAME)) {
            i++ // skip period
            val name = consumeName(VSCodeType.TYPE, 0)
            path = if (path is ClassType && !path.clazz.isTypeAlias()) {
                path.clazz.getOrPut(name, null).typeWithoutArgs
            } else {
                UnresolvedSubType(path!!, name, currPackage, imports)
            }
        }

        return path
    }

    fun consumeName(vsCodeType: VSCodeType, modifiers: Int): String {
        check(tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD)) {
            "Expected name for $vsCodeType"
        }
        val name = tokens.toString(i)
        if (this is ZauberASTBuilder) {
            lsTypes[i] = vsCodeType.ordinal
            lsModifiers[i] = modifiers
        }
        i++
        return name
    }
}