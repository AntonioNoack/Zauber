package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.support.cpp.ast.rich.ArrayType
import me.anno.support.csharp.ast.CSharpASTBuilder.Companion.nativeCSharpTypes
import me.anno.support.java.ast.JavaASTBuilder.Companion.nativeJavaTypes
import me.anno.support.java.ast.NamedCastExpression
import me.anno.support.javascript.ast.FieldOfType
import me.anno.support.javascript.ast.TypeScriptClassScanner
import me.anno.zauber.SpecialFieldNames.ENUM_NAME_NAME
import me.anno.zauber.SpecialFieldNames.ENUM_ORDINAL_NAME
import me.anno.zauber.SpecialFieldNames.OUTER_FIELD_NAME
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.ConstructorHelper.createAssignmentInstructionsForPrimaryConstructor
import me.anno.zauber.ast.rich.DataClassGenerator.finishDataClass
import me.anno.zauber.ast.rich.EnumProperties.readEnumBody
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.binaryOp
import me.anno.zauber.ast.rich.expression.unresolved.*
import me.anno.zauber.ast.rich.expression.unresolved.MemberNameExpression.Companion.nameExpression
import me.anno.zauber.expansion.Macro.evaluateMacro
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Import
import me.anno.zauber.types.LambdaParameter
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType.typeOrNull
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import me.anno.zauber.types.impl.unresolved.UnresolvedSubType
import me.anno.zauber.types.impl.unresolved.UnresolvedType
import kotlin.math.min

abstract class ZauberASTBuilderBase(
    tokens: TokenList, root: Scope,
    val allowUnresolvedTypes: Boolean,
    language: Language
) : ASTBuilderBase(tokens, root, language) {

    companion object {
        private val LOGGER = LogManager.getLogger(ZauberASTBuilderBase::class)

        fun resolveUnresolvedTypes(path: Type?): Type? {
            return path?.resolve()
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

    abstract fun readFileLevel()
    abstract fun readAnnotation(): Annotation

    // todo assign them appropriately
    val annotations = ArrayList<Annotation>()

    open fun readAnnotations() {
        if (consumeIf("@")) {
            annotations.add(readAnnotation())
        }
    }

    fun readLabelMaybe(): String? {
        return if (consumeIf("@")) {
            consumeName(VSCodeType.TYPE, 0)
        } else null
    }

    fun readClassBody(name: String, keywords: FlagSet, scopeType: ScopeType): Scope {
        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)
        classScope.addFlags(keywords)

        readClassBody(classScope)
        return classScope
    }

    fun readClassBody(classScope: Scope) {
        if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            pushBlock(classScope) {
                if (classScope.scopeType == ScopeType.ENUM_CLASS) {
                    val endIndex = readEnumBody()
                    i = min(endIndex + 1, tokens.size) // skipping over semicolon
                }
                readFileLevel()
            }
        }

        val keywords = classScope.flags
        if (keywords.hasFlag(Flags.DATA_CLASS) || keywords.hasFlag(Flags.VALUE)) {
            pushScope(classScope) {
                finishDataClass(classScope)
            }
        }
    }

    open fun readTypeAlias() {
        val newName = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
        val aliasScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        aliasScope.typeParameters = readTypeParameterDeclarations(aliasScope, true)
        aliasScope.hasTypeParameters = true

        consume("=")

        aliasScope.selfAsTypeAlias = readType(null, true)
        popGenericParams()
    }

    fun readTypeOrNull(selfType: Type?): Type? {
        return if (consumeIf(":")) {
            readTypeNotNull(selfType, true)
        } else null
    }

    fun readTypeParameterDeclarations(classScope: Scope, assignToScope: Boolean): List<Parameter> {
        pushGenericParams()
        if (!tokens.equals(i, "<")) {
            if (assignToScope) {
                classScope.typeParameters = emptyList()
                classScope.hasTypeParameters = true
            }
            return emptyList()
        }

        val tmpSelf = classScope.typeWithoutArgs
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

                val type = if (this is ZauberASTBuilder || this is ZauberASTClassScanner) {
                    readTypeOrNull(tmpSelf) ?: Types.NullableAny
                    // if you print type here, typeParameters may not be available yet, and cause an NPE
                } else if (tokens.equals(i, "extends", "super")) {
                    i++ // skip extends
                    readTypeNotNull(null, true)
                } else Types.NullableAny

                if (consumeIf("=")) {
                    // default type???
                    readType(null, true)
                }

                parameters.add(Parameter(parameters.size, name, type, classScope, origin))
                readComma()
            }
        }
        consume(">")
        if (assignToScope) {
            classScope.typeParameters = parameters
            classScope.hasTypeParameters = true
        }

        // replace classScope.typeWithoutArgs with classScope.typeWithArgs
        val properSelf = classScope.typeWithArgs
        for (param in parameters) {
            param.type = param.type.replace(tmpSelf, properSelf)
        }

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

    open fun readValueParametersImpl(): ArrayList<NamedParameter> {
        val parameters = ArrayList<NamedParameter>()
        while (i < tokens.size) {
            val name = if (
                language.allowsDefaultsInParameterDeclaration &&
                tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, "=")
            ) {
                val name = tokens.toString(i)
                i += 2
                name
            } else null
            val value = readExpression()
            val param = NamedParameter(name, value)
            // println("param[${parameters.size}]: $name=$value")
            parameters.add(param)
            if (LOGGER.isDebugEnabled) LOGGER.debug("read param: $param")
            readComma()
        }
        return parameters
    }

    fun createLambdaVariable(type: Type?, name: String, origin: Int): LambdaVariable {
        // to do we neither know type nor initial value :/, both come from the called function/set variable
        val field = currPackage.addField( // this is more of a parameter...
            null, false, isMutable = false, null,
            name, type, null, Flags.NONE, origin
        )
        val variable = LambdaVariable(type, field)
        field.byParameter = variable
        return variable
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

    open fun readIfBranch(): IfElseBranch {
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
                    null, Flags.NONE, origin
                )
                val dstFieldExpr = FieldExpression(dstField, scopeForField, origin)
                val assignment = AssignmentExpression(dstFieldExpr, instanceTest.value)
                val remainder = readBodyOrExpression(null)
                ExpressionList(listOf(assignment, remainder), scopeForField, origin)
            }
        } else readBodyOrExpression(null)
        if (tokens.equals(i + 1, "else")) consumeIf(";")
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
        return WhileLoop(condition, body, label, null)
    }

    fun readDoWhileLoop(label: String?): DoWhileLoop {
        val body = readBodyOrExpression(label ?: "")
        check(tokens.equals(i++, "while"))
        val condition = readExpressionCondition()
        return DoWhileLoop(body = body, condition = condition, label)
    }

    abstract fun readParameterDeclarations(selfType: Type?, extra: List<Parameter>): List<Parameter>

    open fun readTryCatch(): Expression {
        // try with resource
        val origin = origin(i - 1)
        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            // read the declaration...
            val origin = origin(i)
            return pushScope(ScopeType.METHOD_BODY, "tryWithRes") { scope ->
                // todo forbid scope-splitting, so we don't forget any
                val init = pushCall { readMethodBody() }
                val resources = scope.fields

                val catch = readTryCatch()

                // todo close all listed resources in the finally-block
                ExpressionList(listOf(init, catch), scope, origin)
            }
        }

        val tryBody = readBodyOrExpression(null)
        val catches = ArrayList<Catch>()
        while (consumeIf("catch")) {
            val origin = origin(i - 1)
            check(tokens.equals(i, TokenType.OPEN_CALL))
            val catchName = currPackage.generateName("catch", origin)
            pushScope(catchName, ScopeType.METHOD_BODY) {
                val params = pushCall { readParameterDeclarations(null, emptyList()) }
                check(params.size == 1)
                val handler = readBodyOrExpression(null)
                catches.add(Catch(params[0], handler, origin))
            }
        }
        val finally = if (consumeIf("finally")) {
            readBodyOrExpression(null)
        } else null
        return TryCatchBlock(tryBody, catches, finally, currPackage, origin)
    }

    fun readTypeNotNull(selfType: Type?, allowSubTypes: Boolean): Type {
        return readType(selfType, allowSubTypes)
            ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
    }

    open fun readType(
        selfType: Type?, allowSubTypes: Boolean,
        isAndType: Boolean, insideTypeParams: Boolean
    ): Type? {
        val i0 = i
        val negate = consumeIf("!", VSCodeType.OPERATOR, 0)
        if (consumeIf("*", VSCodeType.TYPE, 0)) {
            return UnknownType
        }

        if (language == Language.JAVA) {
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

        when (language) {
            Language.ZAUBER, Language.KOTLIN -> {
                if (consumeIf("?")) {
                    base = typeOrNull(base)
                }
            }
            Language.JAVA, Language.CSHARP -> {
                while (consumeIf(TokenType.OPEN_ARRAY)) {
                    if (consumeIf(TokenType.CLOSE_ARRAY)) {
                        base = Types.Array.withTypeParameter(base)
                    } else {
                        i-- // go one back for pushArray
                        val size = pushArray { readExpression() }
                        base = ArrayType(base, size)
                    }
                }
            }
            Language.TYPESCRIPT -> {
                while (consumeIf(TokenType.OPEN_ARRAY)) {
                    if (consumeIf(TokenType.CLOSE_ARRAY)) {
                        base = Types.Array.withTypeParameter(base)
                    } else {
                        i-- // go one back for pushArray
                        val propertyName = pushArray { readTypeNotNull(selfType, true) }
                        base = FieldOfType(base, propertyName)
                    }
                }
            }
            else -> {}
        }

        if (negate) base = base.not()
        while (consumeIf("&", VSCodeType.OPERATOR, 0)) {
            val typeB = readType(null, allowSubTypes, true, insideTypeParams)
                ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
            base = andTypes(base, typeB)
        }
        if (!isAndType && consumeIf("|", VSCodeType.OPERATOR, 0)) {
            val typeB = readType(null, allowSubTypes, false, insideTypeParams)
                ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
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
        check(i >= endTokenIdx) {
            "Skipped tokens, $i != ${endTokenIdx + 1} at ${tokens.err(i)}"
        }
        i = endTokenIdx + 1 // skip }
        return result
    }

    open fun readTypeExpr(selfType: Type?, allowSubTypes: Boolean, insideTypeParams: Boolean): Type? {

        if (consumeIf("*", VSCodeType.TYPE, 0)) {
            return UnknownType
        }

        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            val endI = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
            val lambdaSymbolForTypes = when {
                this is TypeScriptClassScanner -> "=>"
                else -> "->"
            }
            if (tokens.equals(endI + 1, lambdaSymbolForTypes)) {
                val parameters = pushCall { readZauberLambdaParameters() }
                i = endI + 2 // skip ) and ->
                val returnType = readTypeNotNull(selfType, true)
                return LambdaType(null, parameters, returnType)
            } else {
                return pushCall { readType(selfType, true) }
            }
        }

        if (language.allowsValuesAsTypes) {
            val forbidden = !insideTypeParams && this !is TypeScriptClassScanner
            if (tokens.equals(i, TokenType.NUMBER)) {
                if (forbidden) {
                    throw IllegalStateException("Comptime-Values are only supported in type-params, ${tokens.err(i)}")
                }
                val value = tokens.toString(i++)
                return ComptimeValue(Types.Number, listOf(value))
            }

            if (tokens.equals(i, TokenType.STRING)) {
                if (forbidden) {
                    throw IllegalStateException("Comptime-Values are only supported in type-params, ${tokens.err(i)}")
                }
                val value = tokens.toString(i++)
                return ComptimeValue(Types.String, listOf(value))
            }

            if (tokens.equals(i, "true", "false")) {
                if (forbidden) {
                    throw IllegalStateException("Comptime-Values are only supported in type-params, ${tokens.err(i)}")
                }
                val value = tokens.toString(i++)
                return ComptimeValue(Types.Boolean, listOf(value))
            }
        }

        val path = readTypePath(selfType) // e.g. ArrayList
            ?: return null

        val typeArgs0 = readTypeParameters(selfType)
        if (typeArgs0 != null && path is ClassType && path.typeParameters != null &&
            path.typeParameters.any { it !is GenericType || it.scope != path.clazz }
        ) TODO("Type-args collision/resolution needed: $path + <$typeArgs0>")

        val typeArgs = typeArgs0
            ?: (path as? ClassType)?.typeParameters

        val baseType = when {
            path is ClassType -> ClassType(path.clazz, typeArgs, origin(i))
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
                    isTypeArgsStartingHere(i)
                }"
            )
        }
        // having type arguments means they no longer need to be resolved
        // todo any method call without them must resolve which ones and how many there are, e.g. mapOf, listOf, ...
        if (!isTypeArgsStartingHere(i)) {
            return null
        }

        consume("<")
        if (consumeIf(">")) {
            return if (language == Language.JAVA) {
                null // = unknown
            } else {
                // Kotlin, weird, known but empty
                emptyList()
            }
        }

        val args = ArrayList<Type>()
        while (true) {
            // todo store these (?)
            consumeIf("in")
            consumeIf("out")

            val type0 = if (consumeIf("?")) {
                if (tokens.equals(i, "super", "extends")) {
                    i++ // skip super/extends, I'm not sure about their difference...
                    readType(selfType, allowSubTypes = true, isAndType = false, insideTypeParams = true)
                } else Types.NullableAny
            } else readType(selfType, allowSubTypes = true, isAndType = false, insideTypeParams = true)

            val type = type0
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

    fun readZauberLambdaParameters(): List<LambdaParameter> {
        val result = ArrayList<LambdaParameter>()
        loop@ while (i < tokens.size) {
            if (tokens.equals(i, TokenType.NAME, TokenType.KEYWORD) &&
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
                tokens.equals(i, "<") -> depth++
                tokens.equals(i, ">") -> depth--
                tokens.equals(i, TokenType.OPEN_CALL) -> depth++
                tokens.equals(i, TokenType.CLOSE_CALL) -> depth--
                else -> if (!canAppearInsideAType(i)) {
                    println("Cannot appear inside type: ${tokens.err(i)}")
                    return false
                }
            }
            i++
        }
        return true
    }

    open fun canAppearInsideAType(i: Int): Boolean {
        when {
            tokens.equals(i, TokenType.COMMA) -> {} // ok
            tokens.equals(i, TokenType.NAME) -> {} // ok
            tokens.equals(i, TokenType.STRING) ||
                    tokens.equals(i, TokenType.NUMBER) -> {
            } // comptime values
            tokens.equals(i, "?") ||
                    tokens.equals(i, "->") ||
                    tokens.equals(i, ":") || // names are allowed
                    tokens.equals(i, ".") ||
                    tokens.equals(i, "in") ||
                    tokens.equals(i, "out") ||
                    tokens.equals(i, "*") -> {
                // ok
            }
            tokens.equals(i, TokenType.OPEN_ARRAY) ||
                    tokens.equals(i, TokenType.CLOSE_ARRAY) ||
                    tokens.equals(i, "super") -> {
                // can appear in Java types as List<? super T>
                // or as array notation, e.g. Object[]
                language == Language.JAVA || language == Language.CSHARP
            }
            tokens.equals(i, TokenType.OPEN_BLOCK) ||
                    tokens.equals(i, TokenType.CLOSE_BLOCK) ||
                    tokens.equals(i, TokenType.SYMBOL) ||
                    tokens.equals(i, TokenType.APPEND_STRING) ||
                    tokens.equals(i, "val", "var") ||
                    tokens.equals(i, "else", "fun", "override") ||
                    tokens.equals(i, "this") -> return false
            else -> throw NotImplementedError("Can ${tokens.err(i)} appear inside a type?")
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
            return scope.typeWithArgs
        }
    }

    /**
     * ClassType | SelfType
     * */
    open fun readTypePath(selfType: Type?): Type? {
        if (!(tokens.equals(i, TokenType.NAME) ||
                    this is TypeScriptClassScanner && isKeywordTypeName(i))
        ) return null

        val name = tokens.toString(i++)
        if (this is ZauberASTBuilder) {
            setLSType(i - 1, VSCodeType.TYPE, 0)
        }

        when (language) {
            Language.ZAUBER, Language.KOTLIN -> {
                when (name) {
                    "Self" -> return SelfType((resolveSelfTypeI(selfType) as ClassType).clazz)
                    "This" -> return ThisType(resolveSelfTypeI(selfType))
                }
            }
            Language.CSHARP -> {
                val nativeType = nativeCSharpTypes[name]
                if (nativeType != null) return nativeType
            }
            Language.JAVA -> {
                val nativeType = nativeJavaTypes[name]
                if (nativeType != null) return nativeType
            }
            else -> {}
        }

        var path: Type? = genericParams.last()[name]
        path = path ?: if (allowUnresolvedTypes) {
            UnresolvedType(name, null, currPackage, imports)
        } else {
            resolveTypeByName(selfType, name, currPackage, imports)
                ?: if (tokens.equals(i, ".")) {
                    // get package under root
                    val scope = root.children.firstOrNull { it.name == name }
                    if (scope != null) {
                        scope[ScopeInitType.RESOLVE_TYPES].typeWithArgs
                    } else null
                } else null
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
                path.clazz.getOrPut(name, null).typeWithArgs
            } else {
                UnresolvedSubType(path!!, name, currPackage, imports)
            }
        }

        return path
    }

    open fun consumeName(vsCodeType: VSCodeType, modifiers: Int): String {
        check(tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD)) {
            "Expected name for $vsCodeType at ${tokens.err(i)}"
        }
        val name = tokens.toString(i)
        if (this is ZauberASTBuilder) {
            lsTypes[i] = vsCodeType.ordinal
            lsModifiers[i] = modifiers
        }
        i++
        return name
    }

    abstract fun readSuperCalls(classScope: Scope, readBody: Boolean)

    open fun readClass(scopeType: ScopeType) {
        val origin = origin(i)
        val vsCodeType = when (scopeType) {
            ScopeType.INTERFACE -> VSCodeType.INTERFACE
            ScopeType.ENUM_CLASS -> VSCodeType.ENUM
            else -> VSCodeType.CLASS
        }

        val name = consumeName(vsCodeType, VSCodeModifier.DECLARATION.flag)

        val keywords = packFlags()
        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)

        readTypeParameterDeclarations(classScope, true)
        val privatePrimaryConstructor = consumeIf("private")

        readAnnotations()

        consumeIf("constructor")

        val constructorOrigin = origin(i)
        val constructorParams = readPrimaryConstructorParameters(classScope)
        val constructorBody = createAssignmentInstructionsForPrimaryConstructor(
            classScope, constructorParams, constructorOrigin
        )

        readSuperCalls(classScope, true)

        val primConstructorScope = classScope.getOrCreatePrimaryConstructorScope()
        val primarySuperCall = classScope.superCalls.firstOrNull { it.isClassCall }
        val primaryConstructor = Constructor(
            constructorParams ?: emptyList(),
            primConstructorScope, if (primarySuperCall != null) {
                InnerSuperCall(
                    InnerSuperCallTarget.SUPER,
                    // null means that there is no true primary constructor in this class
                    primarySuperCall.valueParameters ?: emptyList(),
                    origin
                )
            } else null, constructorBody,
            if (privatePrimaryConstructor) Flags.PRIVATE else Flags.NONE,
            constructorOrigin
        )
        primConstructorScope.selfAsConstructor = primaryConstructor

        readClassBody(name, keywords, scopeType)
        popGenericParams()
    }

    fun readPrimaryConstructorParameters(classScope: Scope): List<Parameter>? {
        val scopeType = classScope.scopeType
        val constructorOrigin = origin(i)
        // println("reading primary constructor parameters at ${tokens.err(i)}")
        return if (tokens.equals(i, TokenType.OPEN_CALL)) {
            val constructorScope = classScope.getOrCreatePrimaryConstructorScope()
            pushScope(constructorScope) {
                val selfType = ClassType(classScope, null)
                val extra = getSyntheticParameters(classScope, constructorScope, constructorOrigin)
                pushCall { readParameterDeclarations(selfType, extra) }
            }
        } else if (scopeType == ScopeType.ENUM_CLASS || scopeType == ScopeType.INNER_CLASS) {
            val constructorScope = classScope.getOrCreatePrimaryConstructorScope()
            getSyntheticParameters(classScope, constructorScope, constructorOrigin)
        } else null
    }

    fun getSyntheticParameters(classScope: Scope, constructorScope: Scope, constructorOrigin: Int): List<Parameter> {
        return when (classScope.scopeType) {
            ScopeType.ENUM_CLASS -> {
                // enums additionally store their ID and name
                listOf(
                    Parameter(0, ENUM_ORDINAL_NAME, Types.Int, constructorScope, constructorOrigin),
                    Parameter(1, ENUM_NAME_NAME, Types.String, constructorScope, constructorOrigin)
                )
            }
            ScopeType.INNER_CLASS -> {
                // inner classes store a reference to their outer class
                val outerType = classScope.parent!!.typeWithArgs
                listOf(Parameter(0, OUTER_FIELD_NAME, outerType, constructorScope, constructorOrigin))
            }
            else -> emptyList()
        }
    }

    open fun readInterface() {
        val name = consumeName(VSCodeType.INTERFACE, VSCodeModifier.DECLARATION.flag)
        val classScope = currPackage.getOrPut(name, tokens.fileName, ScopeType.INTERFACE)
        val keywords = packFlags()
        readTypeParameterDeclarations(classScope, true)

        readSuperCalls(classScope, false)
        readClassBody(name, keywords, ScopeType.INTERFACE)
        popGenericParams()
    }

    fun skipTypeParametersToFindFunctionNameAndScope(origin: Int): Scope {
        var j = i
        if (tokens.equals(j, "<")) {
            j = tokens.findBlockEnd(j, "<", ">") + 1
        }
        check(tokens.equals(j, TokenType.NAME))
        val methodName = tokens.toString(j)
        val uniqueName = currPackage.generateName("fun:$methodName", origin)
        return currPackage.getOrPut(uniqueName, tokens.fileName, ScopeType.METHOD)
    }

    fun readAndApplyPackage() {
        val (path, nextI) = tokens.readPath(i)
        markNamespace(nextI)
        currPackage = path
        currPackage.mergeScopeTypes(ScopeType.PACKAGE)
        i = nextI
    }

    fun readAndApplyImport() {
        val (import, nextI) = tokens.readImport(i)
        markNamespace(nextI)
        i = nextI
        applyImport(import)
    }

    fun markNamespace(nextI: Int) {
        if (this is ZauberASTBuilder) {
            for (k in i until nextI) {
                if (tokens.equals(k, TokenType.NAME)) {
                    setLSType(k, VSCodeType.NAMESPACE, 0)
                }
            }
        }
    }

    fun applyImport(import: Import) {
        imports.add(import)
        if (import.allChildren) {
            for (child in import.path.children) {
                currPackage.imports + Import2(child.name, child, false)
            }
        } else {
            currPackage.imports + Import2(import.name, import.path, true)
        }
    }

    fun collectKeywords() {
        if (!tokens.equals(i, TokenType.STRING)) {
            addFlag(consumeKeyword())
            if (this is ZauberASTBuilder) {
                setLSType(i - 1, VSCodeType.KEYWORD, 0)
            }
            return
        }
        throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
    }

    fun readNamedCall(namePath: String, typeParameters: List<Type>?, origin: Int): Expression {
        // constructor or function call with type args
        val isTrueCall = tokens.equals(i, "(")
        val scope = currPackage
        if (isTrueCall) {
            val args = readValueParameters()
            val base = nameExpression(namePath, origin, scope)
            return CallExpression(base, typeParameters, args, origin + 1)
        } else {
            // todo parser bug: we don't support specifying the macro name with dots yet
            //  e.g. mypackage.macro!() doesn't work
            return evaluateMacro(namePath, typeParameters, origin)
        }
    }

    open fun consumeKeyword(): Int {
        throw IllegalStateException("Unknown keyword ${tokens.err(i)}")
    }

}