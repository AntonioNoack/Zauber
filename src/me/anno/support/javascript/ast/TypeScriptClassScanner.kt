package me.anno.support.javascript.ast

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Annotation
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.NullType
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import kotlin.math.max

class TypeScriptClassScanner(tokens: TokenList) :
    ASTClassScanner(tokens) {

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
            when {
                consumeIf("get") -> TODO("read getter at ${tokens.err(i)}")
                consumeIf("set") -> TODO("read setter at ${tokens.err(i)}")
                consumeIf("static") -> isStatic = true
                consumeIf("readonly") -> isReadOnly = true
                consumeIf("public") -> addFlag(Flags.PUBLIC)
                consumeIf("private") -> addFlag(Flags.PRIVATE)
                consumeIf("protected") -> addFlag(Flags.PROTECTED)
                consumeIf("#") -> addFlag(Flags.PRIVATE)
                consumeIf("constructor") -> readConstructor()
                consumeIf("declare") -> {
                    // just a "trust me, this field exists"
                    consume("var")
                    readField()
                }

                tokens.equals(i, TokenType.NAME) || tokens.equals(i, "type") -> {
                    if (tokens.equals(i + 1, "<", "(")) {
                        println("Reading function at ${tokens.err(i)}")
                        readMethod()
                    } else {
                        println("Reading field at ${tokens.err(i)}")
                        readField()
                    }
                }

                consumeIf(";") -> {}
                else -> throw IllegalStateException("Unknown token at ${tokens.err(i)}")
            }
            i = max(i0 + 1, i)
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
            readClassLevel()
        }
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

    override fun readConstructor() {
        val origin = origin(i - 1)
        val flags = packFlags()

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

                var defaultIsNull = false
                val type = if (consumeIf("?:")) {
                    defaultIsNull = true
                    readTypeNotNull(null, true)
                } else if (consumeIf(":")) {
                    readTypeNotNull(null, true)
                } else Types.Any

                val defaultValue = if (consumeIf("=")) {
                    readLazyValue(false)
                } else if (defaultIsNull) {
                    SpecialValueExpression(SpecialValue.NULL, currPackage, origin)
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
            val type = if (consumeIf("extends")) {
                if (consumeIf("keyof")) {
                    val classType = readTypeNotNull(null, true)
                    KeyOfType(classType)
                } else readTypeNotNull(null, true)
            } else Types.NullableAny
            params.add(Parameter(params.size, name, type, currPackage, origin))
            if (!consumeIf(",")) break
        }
        consume(">")
        return params
    }

    override fun readTypeAlias() {
        val newName = consumeName(VSCodeType.TYPE, VSCodeModifier.DECLARATION.flag)
        val aliasScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        aliasScope.typeParameters = readTypeParameterDeclarations(aliasScope, true)
        aliasScope.hasTypeParameters = true

        consume("=")

        aliasScope.selfAsTypeAlias = readType(null, true)
        popGenericParams()
    }

    override fun readTypePath(selfType: Type?): Type? {
        if (consumeIf(TokenType.OPEN_BLOCK)) {
            TODO("Read unnamed class type at ${tokens.err(i - 1)}")
        }
        return super.readTypePath(selfType)
    }

    fun isKeywordTypeName(i: Int): Boolean {
        return tokens.equals(i, "any", "number", "string", "boolean", "undefined", "null", "void")
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