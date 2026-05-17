package me.anno.support.javascript.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.support.Language
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parameter.ParameterExpansion
import me.anno.zauber.ast.rich.parameter.ParameterMutability
import me.anno.zauber.ast.rich.parameter.ParameterType
import me.anno.zauber.ast.rich.parser.ASTClassScanner
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType.Companion.unionTypes
import kotlin.math.max

class TypeScriptClassScanner(tokens: TokenList) :
    ASTClassScanner(tokens, Language.TYPESCRIPT) {

    // todo use these accordingly when reading fields/methods...
    var isStatic = false
    var isReadOnly = false

    override fun readFileLevel() {
        if (currPackage.isClass()) {
            return readClassLevel()
        }

        while (i < tokens.size) {
            val i0 = i
            when {
                consumeIf("import") -> readImport()
                consumeIf("export") -> addFlag(Flags.PUBLIC) // ^^

                consumeIf("class") -> readClass()
                consumeIf("interface") -> readInterface()
                consumeIf("enum") -> readEnum()
                consumeIf("type") -> readTypeAlias()

                consumeIf("function") -> readMethod()

                consumeIf("declare") -> {
                    // just a "trust me, this field/type exists"
                    if (consumeIf("type")) {
                        readTypeAlias()
                    } else {
                        consume("var")
                        readField()
                    }
                }

                tokens.equals(i, TokenType.NAME) -> {
                    // variable / field
                    readField()
                }

                consumeIf(";") -> {}

                else -> throw IllegalStateException("Unknown token at ${tokens.err(i)}")
            }
            i = max(i0 + 1, i)
        }
    }

    private fun readClassLevel() {
        while (i < tokens.size) {
            val i0 = i
            val expectName = tokens.equals(i + 1, ":", "?:", "(")
            when {
                !expectName && consumeIf("get") -> readGetter()
                !expectName && consumeIf("set") -> readSetter()
                consumeIf("static") -> isStatic = true
                consumeIf("readonly") -> isReadOnly = true

                !expectName && consumeIf("public") -> addFlag(Flags.PUBLIC)
                !expectName && consumeIf("private") -> addFlag(Flags.PRIVATE)
                !expectName && consumeIf("protected") -> addFlag(Flags.PROTECTED)

                consumeIf("#") -> addFlag(Flags.PRIVATE)
                consumeIf("constructor") -> readConstructor()
                consumeIf("declare") -> {
                    // just a "trust me, this field exists"
                    consume("var")
                    readField()
                }

                tokens.equals(i, TokenType.NAME) || expectName -> {
                    if (tokens.equals(i + 1, "<", "(")) {
                        println("Reading function at ${tokens.err(i)}")
                        readMethod()
                    } else {
                        println("Reading field at ${tokens.err(i)}")
                        readField()
                    }
                }

                // additional properties are indexed by string (or so), and return some specific value
                consumeIf(TokenType.OPEN_ARRAY) -> readGetProperty()

                consumeIf(";") -> {}
                else -> throw IllegalStateException("Unknown token at ${tokens.err(i)}")
            }
            i = max(i0 + 1, i)
        }
    }

    private fun readGetProperty() {
        val origin = origin(i - 1)
        val flags = packFlags()

        val name = "get"
        val ownerScope = currPackage
        val methodScope = ownerScope.generate(name, origin, ScopeType.METHOD)
        methodScope.addFlags(flags)

        pushScope(methodScope) {

            val typeParams = emptyList<Parameter>()
            methodScope.setTypeParams(typeParams)

            val indexParameter = readParameterDeclaration(
                ownerScope.typeWithArgs, 0,
                ParameterType.VALUE_PARAMETER
            )
            consume(TokenType.CLOSE_ARRAY)

            consume(":")
            val returnType = readType(ownerScope.typeWithArgs, true)

            val body = when {
                tokens.equals(i, TokenType.OPEN_BLOCK) -> readLazyBody()
                consumeIf("=>") -> readLazyValue(false)
                else -> null
            }

            methodScope.selfAsMethod = Method(
                null, false,
                name, typeParams,
                listOf(indexParameter),
                methodScope, returnType,
                emptyList(),
                body, flags, origin
            )
        }
    }

    private fun readGetter() {

        val origin = origin(i - 1)
        val flags = packFlags()

        var name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
        name = "get${name.capitalize()}"

        val ownerScope = currPackage
        val methodScope = ownerScope.generate(name, origin, ScopeType.METHOD)
        methodScope.addFlags(flags)

        pushScope(methodScope) {

            val typeParameters = readTypeParameters()
            methodScope.setTypeParams(typeParameters)

            val valueParameters = readParameterDeclarations(
                ownerScope.typeWithArgs, emptyList(),
                ParameterType.VALUE_PARAMETER
            )

            consume(":")
            val returnType = readType(ownerScope.typeWithArgs, true)

            val body = when {
                tokens.equals(i, TokenType.OPEN_BLOCK) -> readLazyBody()
                consumeIf("=>") -> readLazyValue(false)
                else -> null
            }

            methodScope.selfAsMethod = Method(
                null, false,
                name, typeParameters, valueParameters,
                methodScope, returnType,
                emptyList(),
                body, flags, origin
            )
        }
    }

    private fun readSetter() {

        val origin = origin(i - 1)
        val flags = packFlags()

        var name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)
        name = "set${name.capitalize()}"

        val ownerScope = currPackage
        val methodScope = ownerScope.generate(name, origin, ScopeType.METHOD)
        methodScope.addFlags(flags)

        pushScope(methodScope) {

            val typeParameters = readTypeParameters()
            methodScope.setTypeParams(typeParameters)

            val valueParameters = readParameterDeclarations(
                ownerScope.typeWithArgs, emptyList(),
                ParameterType.VALUE_PARAMETER
            )

            val returnType = if (consumeIf(":")) {
                readType(ownerScope.typeWithArgs, true)
            } else Types.Unit

            val body = when {
                tokens.equals(i, TokenType.OPEN_BLOCK) -> readLazyBody()
                consumeIf("=>") -> readLazyValue(false)
                else -> null
            }

            methodScope.selfAsMethod = Method(
                null, false,
                name, typeParameters, valueParameters,
                methodScope, returnType,
                emptyList(),
                body, flags, origin
            )
        }
    }

    override fun readType(
        selfType: Type?, allowSubTypes: Boolean,
        isAndType: Boolean, insideTypeParams: Boolean
    ): Type? {
        consumeIf("|") // or-types may start with pipes in TypeScript
        println("Reading type at ${tokens.err(i)}")
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
            classScope.setTypeParams(typeParams)

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
            readClassLevel()
        }
    }

    override fun consumeName(vsCodeType: VSCodeType, modifiers: Int): String {
        return if (tokens.equals(i, TokenType.STRING)) {
            // properties allow strings as names
            // setLSType(i,)
            tokens.toString(i++)
        } else super.consumeName(vsCodeType, modifiers)
    }

    override fun readField() {
        val origin = origin(i)
        val flags = packFlags()

        val name = consumeName(VSCodeType.PROPERTY, VSCodeModifier.DECLARATION.flag)

        val owner = currPackage
        val type = if (consumeIf("?:")) {
            // optional field
            val type0 = readTypeNotNull(null, true)
            unionTypes(type0, NullType)
        } else if (consumeIf(":")) {
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

    override fun readMethod() {
        val origin = origin(i - 1)
        val flags = packFlags()

        val name = consumeName(VSCodeType.METHOD, VSCodeModifier.DECLARATION.flag)

        val owner = currPackage
        val methodScope = owner.generate(name, origin, ScopeType.METHOD)
        methodScope.addFlags(flags)

        pushScope(methodScope) {

            val typeParameters = readTypeParameters()
            methodScope.setTypeParams(typeParameters)

            val valueParameters = readParameterDeclarations(
                owner.typeWithArgs, emptyList(),
                ParameterType.VALUE_PARAMETER
            )

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
                name, typeParameters,
                valueParameters,
                methodScope, returnType,
                emptyList(),
                body, flags, origin
            )
        }
    }

    override fun readConstructor() {
        val origin = origin(i - 1)
        val flags = packFlags()

        val classScope = currPackage
        val scope = classScope.generate("constructor", origin, ScopeType.CONSTRUCTOR)

        pushScope(scope) {
            val valueParameters = readParameterDeclarations(
                classScope.typeWithArgs, emptyList(),
                ParameterType.VALUE_PARAMETER
            )

            val body = if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                readLazyBody()
            } else null

            scope.selfAsConstructor = Constructor(valueParameters, scope, null, body, flags, origin)
        }
    }

    override fun readParameterDeclarations(
        selfType: Type?,
        extra: List<Parameter>,
        parameterType: ParameterType
    ): List<Parameter> {
        val params = ArrayList<Parameter>()
        pushCall {
            while (i < tokens.size) {
                params.add(readParameterDeclaration(selfType, params.size, parameterType))
                readComma()
            }
        }
        return params
    }

    private fun readParameterDeclaration(selfType: Type?, index: Int, parameterType: ParameterType): Parameter {
        val origin = origin(i)
        val isVararg = consumeIf("...")
        val name = consumeName(VSCodeType.PARAMETER, 0)

        var defaultIsNull = false
        val type = if (consumeIf("?:")) {
            defaultIsNull = true
            readTypeNotNull(selfType, true)
        } else if (consumeIf(":")) {
            readTypeNotNull(selfType, true)
        } else Types.Any

        val defaultValue = if (consumeIf("=")) {
            readLazyValue(false)
        } else if (defaultIsNull) {
            SpecialValueExpression(SpecialValue.NULL, currPackage, origin)
        } else null

        return Parameter(
            index, ParameterMutability.DEFAULT,
            if (isVararg) ParameterExpansion.VARARG else ParameterExpansion.NONE,
            parameterType, name, type, defaultValue, currPackage, origin
        )
    }

    private fun readTypeParameters(): List<Parameter> {
        if (!consumeIf("<")) return emptyList()
        val params = ArrayList<Parameter>()
        while (!tokens.equals(i, ">")) {
            val origin = origin(i)
            val name = consumeName(VSCodeType.TYPE_PARAM, 0)
            val type = if (consumeIf("extends")) {
                if (consumeIf("keyof")) {
                    val classType = readTypeNotNull(null, true)
                    KeyOfType(classType)
                } else readTypeNotNull(null, true)
            } else Types.NullableAny
            params.add(Parameter(params.size, name, ParameterType.TYPE_PARAMETER, type, currPackage, origin))
            if (!consumeIf(",")) break
        }
        consume(">")
        return params
    }

    override fun readTypeAlias() {
        val newName = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
        val aliasScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        aliasScope.setTypeParams(readTypeParameterDeclarations(aliasScope, true))

        consume("=")

        aliasScope.selfAsTypeAlias = readType(null, true)
        popGenericParams()
    }

    override fun readTypePath(selfType: Type?): Type? {
        if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            return readUnnamedClassType(selfType)
        }
        return super.readTypePath(selfType)
    }

    fun readUnnamedClassType(selfType: Type?): Type {
        return pushBlock(ScopeType.INLINE_CLASS, "?") {
            val superType = if (consumeIf("prototype")) {
                consume(":")
                val superType = readType(selfType, true)
                consume(";")
                superType
            } else null

            readClassLevel() // read all remaining properties
            UnnamedType(superType)
        }
    }

    fun isKeywordTypeName(i: Int): Boolean {
        return tokens.equals(
            i,
            "any",
            "number",
            "string",
            "boolean",
            "undefined",
            "null",
            "void",
            "never" /* = Nothing */
        )
    }

    override fun canAppearInsideAType(i: Int): Boolean {
        return isKeywordTypeName(i) || tokens.equals(i, "|", "{", "}") ||
                super.canAppearInsideAType(i)
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
            readSuperCallsImpl(classScope, readBody)
        }

        if (consumeIf("implements")) {
            readSuperCallsImpl(classScope, readBody)
        }
    }

    override fun readSelfTypeIfPresent(end: Int): Type? = null

}