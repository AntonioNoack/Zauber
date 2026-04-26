package me.anno.support.javascript.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import kotlin.math.max

class TypeScriptClassScanner(tokens: TokenList) :
    ASTClassScanner(tokens) {

    override fun readFileLevel() {
        while (i < tokens.size) {
            val i0 = i
            readTopLevel()
            i = max(i0 + 1, i)
        }
    }

    private fun readTopLevel() {
        when {
            consumeIf("import") -> readImport()
            consumeIf("export") -> addFlag(Flags.PUBLIC) // ^^

            consumeIf("class") -> readClass()
            consumeIf("interface") -> readInterface()
            consumeIf("enum") -> readEnum()
            consumeIf("type") -> readTypeAlias()

            consumeIf("function") -> readFunction()

            tokens.equals(i, TokenType.NAME) -> {
                // variable / field
                readField()
            }

            else -> i++
        }
    }

    override fun readType(
        selfType: Type?, allowSubTypes: Boolean,
        isAndType: Boolean, insideTypeParams: Boolean
    ): Type? {
        consumeIf("|") // or-types may start with pipes in TypeScript
        return super.readType(selfType, allowSubTypes, isAndType, insideTypeParams)
    }

    private fun readClass() {
        val name = consumeName(VSCodeType.CLASS, VSCodeModifier.DECLARATION.flag)
        foundNamedScope(name, Flags.NONE, ScopeType.NORMAL_CLASS)
    }

    private fun readEnum() {
        val name = consumeName(VSCodeType.ENUM, VSCodeModifier.DECLARATION.flag)
        foundNamedScope(name, Flags.NONE, ScopeType.ENUM_CLASS)
    }

    override fun foundNamedScope(
        name: String,
        listenType: Int,
        scopeType: ScopeType
    ) {
        pushNamedScopeLazy(name, listenType, scopeType) { classScope, readBody ->
            val typeParams = readTypeParameters()
            classScope.typeParameters = typeParams
            classScope.hasTypeParameters = true

            readSuperCalls(classScope, readBody)
            readClassBody(classScope, readBody)
        }
    }

    override fun readClassBody(classScope: Scope, readBody: Boolean) {
        if (!tokens.equals(i, TokenType.OPEN_BLOCK)) return

        if (!readBody) {
            skipBlock()
            return
        }

        pushBlock(classScope) {
            while (i < tokens.size) {
                readClassMember()
            }
        }
    }

    private fun readClassMember() {
        val flags = readModifiers()

        when {
            consumeIf("constructor") -> readConstructor(flags)
            consumeIf("function") -> readMethod(flags)
            tokens.equals(i, TokenType.NAME) -> readField(flags)

            else -> i++
        }
    }

    private fun readModifiers(): Int {
        var flags = 0
        while (true) {
            flags = flags or when {
                consumeIf("public") -> Flags.PUBLIC
                consumeIf("private") -> Flags.PRIVATE
                consumeIf("protected") -> Flags.PROTECTED
                // todo TypeScript has static??? -> yes, like in Java
                // consumeIf("static") -> Flags.STATIC
                consumeIf("abstract") -> Flags.ABSTRACT
                // consumeIf("readonly") -> Flags.CONST
                // consumeIf("async") -> Flags.ASYNC
                else -> return flags
            }
        }
    }

    fun readField(flags: Int = packFlags()) {
        val origin = origin(i)

        val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)

        val owner = currPackage

        val type = if (consumeIf(":")) {
            readTypeNotNull(null, true)
        } else null

        val value = if (consumeIf("=")) {
            readLazyValue(true)
        } else null

        val field = owner.addField(
            null, false, true,
            null,
            name,
            type,
            value,
            flags,
            origin
        )

        if (value != null) {
            val ctor = owner.getOrCreatePrimaryConstructorScope()
            val fieldExpr = FieldExpression(field, owner, origin)
            ctor.code.add(AssignmentExpression(fieldExpr, value))
        }

        consumeIf(TokenType.SEMICOLON)
    }

    fun readMethod(flags: Int = packFlags()) {
        val origin = origin(i - 1)

        val name = consumeName(VSCodeType.METHOD, VSCodeModifier.DECLARATION.flag)

        val owner = currPackage
        val methodScope = owner.generate(name, origin, ScopeType.METHOD)
        methodScope.addFlags(flags)

        pushScope(methodScope) {

            val typeParams = readTypeParameters()
            methodScope.typeParameters = typeParams
            methodScope.hasTypeParameters = true

            val params = readParameters()

            val returnType = if (consumeIf(":")) {
                readTypeNotNull(null, true)
            } else null

            val body = when {
                tokens.equals(i, TokenType.OPEN_BLOCK) -> readLazyBody()
                consumeIf("=>") -> readLazyValue(false)
                else -> null
            }

            methodScope.selfAsMethod = Method(
                null, false,
                name, typeParams,
                params,
                methodScope, returnType,
                emptyList(),
                body, flags, origin
            )
        }
    }

    private fun readFunction() {
        readMethod()
    }

    private fun readConstructor(flags: Int) {
        val origin = origin(i - 1)

        val classScope = currPackage
        val scope = classScope.generate("constructor", origin, ScopeType.CONSTRUCTOR)

        pushScope(scope) {
            val params = readParameters()

            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                readLazyBody()
            } else null

            scope.selfAsConstructor = Constructor(
                params, scope, null, body, flags, origin
            )
        }
    }

    private fun readParameters(): List<Parameter> {
        val params = ArrayList<Parameter>()

        pushCall {
            while (i < tokens.size) {

                val origin = origin(i)
                val name = consumeName(VSCodeType.PARAMETER, 0)

                val type = if (consumeIf(":")) {
                    readTypeNotNull(null, true)
                } else Types.Any

                val defaultValue = if (consumeIf("=")) {
                    readLazyValue(false)
                } else null

                params.add(
                    Parameter(
                        params.size,
                        false, false, false,
                        name, type, defaultValue,
                        currPackage, origin
                    )
                )

                readComma()
            }
        }

        return params
    }

    private fun readTypeParameters(): List<Parameter> {
        if (!consumeIf("<")) return emptyList()
        val params = ArrayList<Parameter>()
        while (!tokens.equals(i, ">")) {
            val origin = origin(i)
            val name = consumeName(VSCodeType.TYPE_PARAM, 0)
            params.add(Parameter(params.size, name, Types.NullableAny, currPackage, origin))
            readComma()
        }
        consume(">")
        return params
    }

    override fun readTypeAlias() {
        val newName = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
        val aliasScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        aliasScope.typeParameters = readTypeParameterDeclarations(aliasScope)
        aliasScope.hasTypeParameters = true

        consume("=")

        aliasScope.selfAsTypeAlias = readType(null, true)
        popGenericParams()
    }

    override fun canAppearInsideAType(i: Int): Boolean {
        return tokens.equals(i, "number") || super.canAppearInsideAType(i)
    }

    override fun readExpression(minPrecedence: Int): Expression =
        readLazyValue(false)

    override fun readBodyOrExpression(label: String?): Expression {
        throw NotImplementedError()
    }

    override fun readAnnotation(): Annotation {
        throw NotImplementedError()
    }

    override fun readMethodBody(): ExpressionList {
        throw NotImplementedError()
    }

    override fun readSuperCalls(classScope: Scope, readBody: Boolean) {
        if (consumeIf("extends")) {
            super.readSuperCalls(classScope, readBody)
        }

        if (consumeIf("implements")) {
            super.readSuperCalls(classScope, readBody)
        }
    }

    override fun readSelfTypeIfPresent(end: Int): Type? = null

}