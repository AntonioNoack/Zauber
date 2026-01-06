package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.DataClassGenerator.finishDataClass
import me.anno.zauber.ast.rich.FieldGetterSetter.finishLastField
import me.anno.zauber.ast.rich.FieldGetterSetter.readGetter
import me.anno.zauber.ast.rich.FieldGetterSetter.readSetter
import me.anno.zauber.ast.rich.controlflow.*
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.MemberNameExpression.Companion.nameExpression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.TypeResolution.getSelfType
import me.anno.zauber.types.*
import me.anno.zauber.types.BooleanUtils.not
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.ArrayType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.NumberType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.Types.UnitType
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.impl.AndType.Companion.andTypes
import me.anno.zauber.types.impl.NullType.typeOrNull
import me.anno.zauber.types.impl.UnionType.Companion.unionTypes
import kotlin.math.max
import kotlin.math.min

// I want macros... how could we implement them? learn about Rust macros
//  -> we get tokens as attributes in a specific pattern,
//  and after resolving the pattern, we can copy-paste these pattern variables as we please
//  -> we should be able to implement when() and for() using these
//  -> do we even need macros when we have a good language? :)

class ASTBuilder(val tokens: TokenList, val root: Scope) {

    companion object {

        private val LOGGER = LogManager.getLogger(ASTBuilder::class)

        val synthetic = "synthetic"
        val syntheticList = listOf(synthetic)

        val fileLevelKeywords = listOf(
            "enum", "private", "protected", "fun", "class", "data", "value",
            "companion", "object", "constructor", "inline",
            "override", "abstract", "open", "final", "operator",
            "const", "lateinit", "annotation", "internal", "inner", "sealed",
            "infix", "external"
        )

        val paramLevelKeywords = listOf(
            "private", "protected", "var", "val", "open", "override",
            "crossinline", "vararg", "final"
        )

        val supportedInfixFunctions = listOf(
            // these shall only be supported for legacy reasons: I dislike that their order of precedence isn't clear
            "shl", "shr", "ushr", "and", "or", "xor",

            // I like these:
            "in", "to", "step", "until", "downTo",
            "is", "!is", "as", "as?"
        )

        var debug = false

        val unitInstance by lazy {
            val scope = UnitType.clazz
            val field = scope.objectField!!
            FieldExpression(field, scope, -1)
        }
    }

    val imports = ArrayList<Import>()
    val keywords = ArrayList<String>()
    val genericParams = ArrayList<HashMap<String, GenericType>>()

    init {
        genericParams.add(HashMap())
    }

    // todo assign them appropriately
    val annotations = ArrayList<Annotation>()

    var currPackage = root
    var i = 0

    /**
     * ClassType | SelfType
     * */
    fun readTypePath(selfType: Type?): Type? {
        check(tokens.equals(i, TokenType.NAME))
        val name0 = tokens.toString(i++)
        if (name0 == "Self") {// Special-meaning type
            if (selfType is ClassType) {
                return SelfType(selfType.clazz)
            } else {
                check(selfType == null)
                var scope = currPackage
                while (scope.scopeType?.isClassType() != true) {
                    scope = scope.parent
                        ?: throw IllegalStateException("Could not resolve Self-type in $currPackage at ${tokens.err(i - 1)}")
                }
                return SelfType(scope)
            }
        }

        var path = genericParams.last()[name0]
            ?: currPackage.resolveTypeOrNull(name0, this)
            ?: (selfType as? ClassType)?.clazz?.resolveType(name0, this)
            ?: run {
                i--
                println("Unresolved type '$name0' in $currPackage/$selfType at ${tokens.err(i)}")
                return null
            }

        while (tokens.equals(i, ".") && tokens.equals(i + 1, TokenType.NAME)) {
            path = (path as ClassType).clazz.getOrPut(tokens.toString(i + 1), null).typeWithoutArgs
            i += 2 // skip period and name
        }
        return path
    }

    private fun readClass() {
        check(tokens.equals(++i, TokenType.NAME))
        val name = tokens.toString(i++)

        val keywords = packKeywords()
        val scopeType =
            if ("enum" in keywords) ScopeType.ENUM_CLASS
            else ScopeType.NORMAL_CLASS


        val clazz = currPackage.getOrPut(name, tokens.fileName, scopeType)

        val typeParameters = readTypeParameterDeclarations(clazz)
        clazz.typeParameters = typeParameters
        clazz.hasTypeParameters = true

        val privatePrimaryConstructor = tokens.equals(i, "private")
        if (privatePrimaryConstructor) i++

        readAnnotations()

        if (tokens.equals(i, "constructor")) i++
        val constructorOrigin = origin(i)
        val constructorParams = if (tokens.equals(i, TokenType.OPEN_CALL)) {
            val primScope = clazz.getOrCreatePrimConstructorScope()
            pushScope(primScope) {
                val selfType = ClassType(clazz, null)
                pushCall { readParamDeclarations(selfType, primScope) }
            }
        } else null

        val needsSuperCall = clazz != AnyType.clazz
        readSuperCalls(clazz, needsSuperCall)

        if (constructorParams != null) {
            val prim = clazz.getOrCreatePrimConstructorScope()
            for (field in prim.fields) {
                clazz.addField(field)
            }
        }

        val scope = clazz.getOrCreatePrimConstructorScope()
        val primaryConstructor = Constructor(
            constructorParams ?: emptyList(),
            scope, null, null,
            if (privatePrimaryConstructor) listOf("private") else emptyList(),
            constructorOrigin
        )
        scope.selfAsConstructor = primaryConstructor

        readClassBody(name, keywords, scopeType)
        popGenericParams()
    }

    inline fun <R> pushScope(scopeType: ScopeType, prefix: String, callback: (Scope) -> R): R {
        val name = currPackage.generateName(prefix)
        return pushScope(name, scopeType, callback)
    }

    inline fun <R> pushScope(name: String, scopeType: ScopeType, callback: (Scope) -> R): R {
        val parent = currPackage
        val child = parent.getOrPut(name, scopeType)
        currPackage = child
        val value = callback(child)
        currPackage = parent
        return value
    }

    private inline fun <R> pushScope(scope: Scope, callback: () -> R): R {
        val parent = currPackage
        currPackage = scope
        val value = callback()
        currPackage = parent
        return value
    }

    private fun readInterface() {
        keywords.add("interface")
        check(tokens.equals(++i, TokenType.NAME))
        val name = tokens.toString(i++)
        val clazz = currPackage.getOrPut(name, tokens.fileName, ScopeType.INTERFACE)
        val keywords = packKeywords()
        clazz.typeParameters = readTypeParameterDeclarations(clazz)
        clazz.hasTypeParameters = true

        readSuperCalls(clazz, false)
        readClassBody(name, keywords, ScopeType.INTERFACE)
        popGenericParams()
    }

    private fun readAnnotations() {
        if (tokens.equals(i, "@")) {
            annotations.add(readAnnotation())
        }
    }

    private fun readObject() {
        val origin = origin(i)
        val name = if (tokens.equals(++i, TokenType.NAME)) {
            tokens.toString(i++)
        } else if (keywords.remove("companion")) {
            "Companion"
        } else throw IllegalStateException("Missing object name")
        keywords.add("object")
        val keywords = packKeywords()

        val scope = currPackage.getOrPut(name, tokens.fileName, ScopeType.OBJECT)
        readSuperCalls(scope, true)
        readClassBody(name, keywords, ScopeType.OBJECT)

        scope.hasTypeParameters = true // no type-params are supported
        scope.objectField = Field(
            scope, null, false, null, "__instance__",
            ClassType(scope, emptyList()),
            /* todo should we set initialValue? */ null, emptyList(), origin
        )
    }

    private fun readSuperCalls(clazz: Scope, needsEntry: Boolean) {
        if (tokens.equals(i, ":")) {
            i++ // skip :
            var endIndex = findEndOfSuperCalls(i)
            if (endIndex < 0) endIndex = tokens.size
            push(endIndex) {
                while (i < tokens.size) {
                    clazz.superCalls.add(readSuperCall(clazz.typeWithoutArgs))
                    readComma()
                }
            }
            i = endIndex // index of {
        }
        if (needsEntry && clazz.superCalls.isEmpty()) {
            clazz.superCalls.add(SuperCall(AnyType, emptyList(), null))
        }
    }

    /**
     * looks for new keywords, or reading depth < 0 by brackets
     * */
    private fun findEndOfSuperCalls(i0: Int): Int {
        var depth = 0
        for (i in i0 until tokens.size) {
            if (depth == 0) {
                if (tokens.equals(i, TokenType.OPEN_BLOCK) ||
                    tokens.equals(i, TokenType.OPEN_ARRAY) ||
                    tokens.equals(i, TokenType.KEYWORD) ||
                    fileLevelKeywords.any { tokens.equals(i, it) }
                ) return i
            }
            when {
                tokens.equals(i, TokenType.OPEN_BLOCK) ||
                        tokens.equals(i, TokenType.OPEN_ARRAY) ||
                        tokens.equals(i, TokenType.OPEN_CALL) -> depth++
                tokens.equals(i, TokenType.CLOSE_BLOCK) ||
                        tokens.equals(i, TokenType.CLOSE_ARRAY) ||
                        tokens.equals(i, TokenType.CLOSE_CALL) -> depth--
            }
        }
        return -1
    }

    private fun readClassBody(name: String, keywords: List<String>, scopeType: ScopeType): Scope {
        val classScope = currPackage.getOrPut(name, tokens.fileName, scopeType)
        classScope.keywords.addAll(keywords)

        if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            pushBlock(classScope) {
                if ("enum" in keywords) {
                    val endIndex = readEnumBody()
                    i = min(endIndex + 1, tokens.size) // skipping over semicolon
                }
                readFileLevel()
            }
        }

        if ("data" in keywords || "value" in keywords) {
            pushScope(classScope) {
                finishDataClass(classScope)
            }
        }

        return classScope
    }

    private fun readEnumBody(): Int {

        val origin0 = origin(i)
        var endIndex = tokens.findToken(i, ";")
        if (endIndex < 0) endIndex = tokens.size
        val classScope = currPackage
        val scope = classScope.getOrPut("Companion", ScopeType.OBJECT)
        push(endIndex) {
            var ordinal = 0
            while (i < tokens.size) {
                // read enum value
                readAnnotations()
                check(tokens.equals(i, TokenType.NAME))

                val origin = origin(i)
                val name = tokens.toString(i++)
                val typeParameters = readTypeParameters(null)
                val valueParameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                    pushCall { readParamExpressions() }
                } else emptyList()

                val keywords = packKeywords()
                val entryScope = readClassBody(name, emptyList(), ScopeType.ENUM_ENTRY_CLASS)
                // todo add name and id as parameters
                val extraValueParameters = listOf(
                    NamedParameter(null, NumberExpression((ordinal++).toString(), scope, origin)),
                    NamedParameter(null, StringExpression(name, scope, origin)),
                )
                val initialValue = ConstructorExpression(
                    classScope, typeParameters,
                    extraValueParameters + valueParameters,
                    null, classScope, origin
                )
                entryScope.objectField = Field(
                    scope, scope.typeWithoutArgs, false, null,
                    name, classScope.typeWithoutArgs, initialValue, keywords, origin
                )

                readComma()
            }
        }

        createEnumProperties(scope, classScope, origin0)
        return endIndex
    }

    private fun createEnumProperties(scope: Scope, enumScope: Scope, origin: Int) {

        // todo we also need to add then as constructor properties,
        //  and add them into all constructors...
        Field(
            enumScope, enumScope.typeWithoutArgs, false, null,
            "name", StringType, null, syntheticList, origin
        )

        Field(
            enumScope, enumScope.typeWithoutArgs, false, null,
            "ordinal", IntType, null, syntheticList, origin
        )

        scope.hasTypeParameters = true

        val listType = ClassType(ListType.clazz, listOf(enumScope.typeWithoutArgs))
        val initialValue = CallExpression(
            MemberNameExpression("listOf", scope, origin),
            listOf(listType), enumScope.enumValues.map {
                val field = it.objectField!!
                val expr = FieldExpression(field, scope, origin)
                NamedParameter(null, expr)
            }, origin
        )

        val entriesField = Field(
            scope, scope.typeWithoutArgs, false,
            null, "__entries", listType,
            initialValue, syntheticList, origin
        )

        pushScope(scope) {
            pushScope(ScopeType.METHOD, "entries") { methodScope ->
                methodScope.selfAsMethod = Method(
                    scope.typeWithoutArgs, "entries", emptyList(), emptyList(),
                    methodScope, listType, emptyList(), FieldExpression(entriesField, methodScope, origin),
                    syntheticList, origin
                )
            }
        }
    }

    var lastField: Field? = null

    private fun readFieldInClass(isMutable: Boolean) {
        val origin = origin(i++)// skip var/val

        val fieldScope = currPackage // todo is this fine??
        val typeParameters = readTypeParameterDeclarations(fieldScope)
        val selfType = readFieldOrMethodSelfType(typeParameters, fieldScope)
            ?: getSelfType(fieldScope)

        check(tokens.equals(i, TokenType.NAME))
        val name = tokens.toString(i++)

        val keywords = packKeywords()
        val valueType = if (tokens.equals(i, ":")) {
            i++
            readType(selfType, true)
        } else null

        val primScope = fieldScope.getOrCreatePrimConstructorScope()
        val initialValue = pushScope(primScope) {
            // println("Fields in primary constructor for $primScope: ${primScope.fields}")
            if (tokens.equals(i, "=")) {
                i++
                // todo find reasonable end index, e.g. fun, class, private, object, and limit to that
                readExpression()
            } else if (tokens.equals(i, "by")) {
                i++
                // todo find reasonable end index, e.g. fun, class, private, object, and limit to that
                DelegateExpression(readExpression())
            } else null
        }

        val field = Field(
            currPackage, selfType, isMutable, null,
            name, valueType, initialValue, keywords, origin
        )
        field.typeParameters = typeParameters
        if (LOGGER.enableDebug) LOGGER.debug("read field $name: $valueType = $initialValue")

        finishLastField()
        lastField = field
    }

    private fun skipTypeParametersToFindFunctionNameAndScope(): Scope {
        var j = i
        if (tokens.equals(j, "<")) {
            j = tokens.findBlockEnd(j, "<", ">") + 1
        }
        check(tokens.equals(j, TokenType.NAME))
        val name = tokens.toString(j)
        val name1 = currPackage.generateName("f:$name")
        return currPackage.getOrPut(name1, tokens.fileName, ScopeType.METHOD)
    }

    private fun readFieldOrMethodSelfType(typeParameters: List<Parameter>, functionScope: Scope): Type? {
        if (tokens.equals(i + 1, ".") ||
            tokens.equals(i + 1, "<") ||
            tokens.equals(i + 1, "?.")
        ) {
            if (tokens.equals(i + 1, ".")) {
                // avoid packing both type and function name into one
                val name = tokens.toString(i++)
                val type = currPackage.resolveType(
                    name, typeParameters,
                    functionScope, this,
                )
                check(tokens.equals(i++, "."))
                return type
            } else {
                var type = readType(null, false)
                    ?: return null
                if (tokens.equals(i, "?.")) {
                    type = typeOrNull(type)
                    i++
                } else {
                    check(tokens.equals(i++, "."))
                }
                return type
            }
        } else return null
    }

    private fun readWhereConditions(): List<TypeCondition> {
        return if (tokens.equals(i, "where")) {
            i++ // skip where
            val conditions = ArrayList<TypeCondition>()
            while (true) {

                check(tokens.equals(i, TokenType.NAME))
                check(tokens.equals(i + 1, ":"))

                val name = tokens.toString(i++)
                i++ // skip comma
                val type = readTypeNotNull(null, true)
                conditions.add(TypeCondition(name, type))

                if (tokens.equals(i, ",") &&
                    tokens.equals(i + 1, TokenType.NAME) &&
                    tokens.equals(i + 2, ":")
                ) {
                    i++ // skip comma and continue reading conditions
                } else {
                    // done
                    break
                }
            }
            conditions
        } else emptyList()
    }

    private fun readMethod(): Method {
        val origin = origin(i++) // skip 'fun'

        val keywords = packKeywords()

        // parse optional <T, U>
        val clazz = currPackage
        val methodScope = skipTypeParametersToFindFunctionNameAndScope()
        val typeParameters = readTypeParameterDeclarations(methodScope)

        check(tokens.equals(i, TokenType.NAME))
        val selfType = readFieldOrMethodSelfType(typeParameters, methodScope)
            ?: getSelfType(methodScope)

        check(tokens.equals(i, TokenType.NAME))
        val name = tokens.toString(i++)

        if (LOGGER.enableDebug) LOGGER.debug("fun <$typeParameters> ${if (selfType != null) "$selfType." else ""}$name(...")

        // parse parameters (...)
        check(tokens.equals(i, TokenType.OPEN_CALL)) {
            "Expected () for method call $selfType.$name, but found ${tokens.err(i)}"
        }

        lateinit var parameters: List<Parameter>
        pushScope(methodScope) {
            parameters = pushCall {
                val selfType = ClassType(clazz, null)
                readParamDeclarations(selfType, methodScope)
            }
        }

        // optional return type
        var returnType = if (tokens.equals(i, ":")) {
            i++ // skip :
            readType(selfType, true)
        } else null

        val extraConditions = readWhereConditions()

        // body (or just = expression)
        val body = pushScope(methodScope) {
            if (tokens.equals(i, "=")) {
                val origin = origin(i++) // skip =
                ReturnExpression(readExpression(), null, methodScope, origin)
            } else if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                if (returnType == null) returnType = UnitType
                pushBlock(ScopeType.METHOD_BODY, methodScope.name) { readMethodBody() }
            } else {
                if (returnType == null) returnType = UnitType
                null
            }
        }

        popGenericParams()

        val method = Method(
            selfType, name, typeParameters, parameters, methodScope,
            returnType, extraConditions, body, keywords, origin
        )
        methodScope.selfAsMethod = method
        return method
    }

    private fun readConstructor(): Constructor {
        i++ // skip 'constructor'

        val origin = origin(i)
        val keywords = packKeywords()

        if (LOGGER.enableDebug) LOGGER.debug("constructor(...")

        // parse parameters (...)
        check(tokens.equals(i, TokenType.OPEN_CALL))
        lateinit var parameters: List<Parameter>
        val classScope = currPackage
        val constructorScope = pushScope("constructor", ScopeType.CONSTRUCTOR_PARAMS) { scope ->
            val selfType = ClassType(classScope, null)
            parameters = pushCall { readParamDeclarations(selfType, scope) }
            scope
        }

        // optional return type
        val superCall = if (tokens.equals(i, ":")) {
            i++
            check(tokens.equals(i, "this") || tokens.equals(i, "super"))
            val origin = origin(i)
            val target = if (tokens.equals(i++, "super"))
                InnerSuperCallTarget.SUPER else InnerSuperCallTarget.THIS
            // val typeParams = readTypeParams(null) // <- not supported
            val params = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                pushCall { readParamExpressions() }
            } else emptyList()
            InnerSuperCall(target, params, classScope, origin)
        } else null

        // body (or just = expression)
        val body = pushScope(constructorScope) {
            if (tokens.equals(i, "=")) {
                i++ // skip =
                readExpression()
            } else if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                pushBlock(ScopeType.CONSTRUCTOR, null) { readMethodBody() }
            } else null
        }

        val constructor = Constructor(
            parameters, constructorScope,
            superCall, body, keywords, origin
        )
        constructorScope.selfAsConstructor = constructor
        return constructor
    }

    private fun applyImport(import: Import) {
        imports.add(import)
        if (import.allChildren) {
            for (child in import.path.children) {
                currPackage.imports += Import2(child.name, child, false)
            }
        } else {
            currPackage.imports += Import2(import.name, import.path, true)
        }
    }

    fun readFileLevel() {
        loop@ while (i < tokens.size) {
            if (LOGGER.enableDebug) LOGGER.debug("readFileLevel[$i]: ${tokens.err(i)}")
            when {
                tokens.equals(i, "package") -> {
                    val (path, nextI) = tokens.readPath(i)
                    currPackage = path
                    currPackage.mergeScopeTypes(ScopeType.PACKAGE)
                    i = nextI
                }

                tokens.equals(i, "import") -> {
                    val (import, nextI) = tokens.readImport(i)
                    i = nextI
                    applyImport(import)
                }

                tokens.equals(i, "class") -> readClass()
                tokens.equals(i, "object") -> readObject()
                tokens.equals(i, "fun") -> {
                    if (tokens.equals(i + 1, "interface")) {
                        keywords.add("fun"); i++
                        readInterface()
                    } else {
                        println("reading method at ${tokens.err(i)}")
                        readMethod()
                    }
                }
                tokens.equals(i, "interface") -> readInterface()
                tokens.equals(i, "constructor") -> readConstructor()
                tokens.equals(i, "typealias") -> readTypeAlias()
                tokens.equals(i, "var") -> readFieldInClass(true)
                tokens.equals(i, "val") -> readFieldInClass(false)
                tokens.equals(i, "init") -> {
                    check(tokens.equals(++i, TokenType.OPEN_BLOCK))
                    pushBlock(currPackage.getOrCreatePrimConstructorScope()) {
                        readMethodBody()
                    }
                }

                tokens.equals(i, "get") -> readGetter()
                tokens.equals(i, "set") -> readSetter()

                tokens.equals(i, "@") -> annotations.add(readAnnotation())

                tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD) ->
                    collectNames(fileLevelKeywords)

                tokens.equals(i, ";") -> i++ // just skip it

                else -> throw NotImplementedError("Unknown token at ${tokens.err(i)}")
            }
        }
        finishLastField()
    }

    fun readTypeAlias() {
        check(tokens.equals(i++, "typealias"))
        check(tokens.equals(i, TokenType.NAME))
        val newName = tokens.toString(i++)
        val pseudoScope = currPackage.getOrPut(newName, tokens.fileName, ScopeType.TYPE_ALIAS)
        pseudoScope.typeParameters = readTypeParameterDeclarations(pseudoScope)

        check(tokens.equals(i++, "="))
        val trueType = readType(null, true)
        pseudoScope.typeAlias = trueType
    }

    fun readAnnotation(): Annotation {
        check(tokens.equals(i++, "@"))
        if (tokens.equals(i, TokenType.NAME) &&
            tokens.equals(i + 1, ":") &&
            tokens.equals(i + 2, TokenType.NAME)
        ) {
            // skipping scope
            i += 2
        }
        check(tokens.equals(i, TokenType.NAME))
        val path = readTypePath(null)
            ?: throw IllegalStateException("Expected type for annotation at ${tokens.err(i)}")
        val params = if (tokens.equals(i, TokenType.OPEN_CALL)) {
            pushCall { readParamExpressions() }
        } else emptyList()
        return Annotation(path, params)
    }

    fun packKeywords(): List<String> {
        if (keywords.isEmpty()) return emptyList()
        val tmp = ArrayList(keywords)
        keywords.clear()
        return tmp
    }

    fun collectNames(keywords1: List<String>) {
        for (keyword in keywords1) {
            if (!tokens.equals(i, TokenType.STRING) &&
                tokens.equals(i, keyword)
            ) {
                keywords.add(keyword)
                i++
                return
            }
        }

        throw IllegalStateException("Unknown keyword ${tokens.toString(i)} at ${tokens.err(i)}")
    }

    fun readParamExpressions(): ArrayList<NamedParameter> {
        val params = ArrayList<NamedParameter>()
        while (i < tokens.size) {
            val name = if (tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, "=")
            ) tokens.toString(i).apply { i += 2 } else null
            val value = readExpression()
            val param = NamedParameter(name, value)
            params.add(param)
            if (LOGGER.enableDebug) LOGGER.debug("read param: $param")
            readComma()
        }
        return params
    }

    fun readParamDeclarations(
        selfType: Type?,
        secondaryScope: Scope,
    ): List<Parameter> {
        // todo when this has its own '=', this needs its own scope...,
        //  and that scope could be inherited by the function body...
        val result = ArrayList<Parameter>()
        loop@ while (i < tokens.size) {
            when {
                tokens.equals(i, "@") -> annotations.add(readAnnotation())
                tokens.equals(i, TokenType.NAME) || tokens.equals(i, TokenType.KEYWORD) -> {
                    val origin = origin(i)
                    val name = tokens.toString(i++)
                    if (name in paramLevelKeywords &&
                        (tokens.equals(i, TokenType.NAME) ||
                                tokens.equals(i, TokenType.KEYWORD))
                    ) {
                        keywords.add(name)
                        continue@loop
                    }

                    val isVar = keywords.remove("var")
                    val isVal = keywords.remove("val")
                    val isVararg = keywords.remove("vararg")
                    check(tokens.equals(i++, ":")) { "Expected colon in var/val at ${tokens.err(i - 1)}" }

                    var type = readTypeNotNull(null, true) // <-- handles generics now

                    if (isVararg) type = ClassType(ArrayType.clazz, listOf(type))

                    val initialValue = if (tokens.equals(i, "=")) {
                        i++
                        readExpression()
                    } else null

                    val keywords = packKeywords()
                    val parameter = Parameter(isVar, isVal, isVararg, name, type, initialValue, currPackage, origin)
                    result.add(parameter)

                    val fieldScope = if (isVar || isVal) currPackage else secondaryScope
                    // automatically gets added to fieldScope
                    parameter.field = Field(
                        fieldScope, selfType, isVar, if (isVar || isVal) null else parameter,
                        name, type, initialValue, keywords, origin
                    )

                    readComma()
                }
                else -> throw NotImplementedError("Unknown token in params at ${tokens.err(i)}")
            }
        }
        return result
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

    fun pushGenericParams() {
        genericParams.add(HashMap(genericParams.last()))
    }

    fun popGenericParams() {
        genericParams.removeLast()
    }

    fun readTypeParameterDeclarations(scope: Scope): List<Parameter> {
        pushGenericParams()
        if (!tokens.equals(i, "<")) return emptyList()
        val params = ArrayList<Parameter>()
        tokens.push(i++, "<", ">") {
            while (i < tokens.size) {
                // todo store & use these?
                if (tokens.equals(i, "in")) i++
                if (tokens.equals(i, "out")) i++

                check(tokens.equals(i, TokenType.NAME)) { "Expected type parameter name" }
                val origin = origin(i)
                val name = tokens.toString(i++)

                // name might be needed for the type, so register it already here
                genericParams.last()[name] = GenericType(scope, name)

                val type = if (tokens.equals(i, ":")) {
                    i++ // skip :
                    readType(null, true)
                        ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
                } else NullableAnyType

                params.add(Parameter(name, type, scope, origin))
                readComma()
            }
        }
        check(tokens.equals(i++, ">")) // skip >
        scope.typeParameters = params
        scope.hasTypeParameters = true
        return params
    }

    fun readExpressionCondition(): Expression {
        return pushCall { readExpression() }
    }

    fun readBodyOrExpression(): Expression {
        return if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
            // if just names and -> follow, read a single expression instead
            // if a destructuring and -> follow, read a single expression instead
            var j = i + 1
            var depth = 0
            arrowSearch@ while (j < tokens.size) {
                when {
                    tokens.equals(j, TokenType.OPEN_CALL) -> depth++
                    tokens.equals(j, TokenType.CLOSE_CALL) -> depth--
                    tokens.equals(j, "*") ||
                            tokens.equals(j, "?") ||
                            tokens.equals(j, ".") ||
                            tokens.equals(j, TokenType.COMMA) ||
                            tokens.equals(j, TokenType.NAME) -> {
                    }
                    tokens.equals(j, "->") -> {
                        if (depth == 0) {
                            return readExprInNewScope()
                        }
                    }
                    else -> break@arrowSearch
                }
                j++
            }

            pushBlock(ScopeType.EXPRESSION, null) {
                readMethodBody()
            }
        } else {
            readExprInNewScope()
        }
    }

    private fun readExprInNewScope(): Expression {
        return pushScope(currPackage.generateName("expr"), ScopeType.EXPRESSION) {
            readExpression()
        }
    }

    fun origin(i: Int): Int {
        return TokenListIndex.getIndex(tokens, i)
    }

    private fun readPrefix(): Expression {

        val label =
            if (tokens.equals(i, TokenType.LABEL)) tokens.toString(i++)
            else null

        return when {
            tokens.equals(i, "@") -> {
                val annotation = readAnnotation()
                AnnotatedExpression(annotation, readPrefix())
            }
            tokens.equals(i, "null") -> SpecialValueExpression(SpecialValue.NULL, currPackage, origin(i++))
            tokens.equals(i, "true") -> SpecialValueExpression(SpecialValue.TRUE, currPackage, origin(i++))
            tokens.equals(i, "false") -> SpecialValueExpression(SpecialValue.FALSE, currPackage, origin(i++))
            tokens.equals(i, "this") -> SpecialValueExpression(SpecialValue.THIS, currPackage, origin(i++))
            tokens.equals(i, "super") -> SpecialValueExpression(SpecialValue.SUPER, currPackage, origin(i++))
            tokens.equals(i, TokenType.NUMBER) -> NumberExpression(tokens.toString(i), currPackage, origin(i++))
            tokens.equals(i, TokenType.STRING) -> StringExpression(tokens.toString(i), currPackage, origin(i++))
            tokens.equals(i, "!") -> {
                val origin = origin(i++)
                val base = readExpression()
                NamedCallExpression(base, "not", null, emptyList(), currPackage, origin)
            }
            tokens.equals(i, "+") -> {
                val origin = origin(i++)
                val base = readExpression()
                NamedCallExpression(base, "unaryPlus", null, emptyList(), currPackage, origin)
            }
            tokens.equals(i, "-") -> {
                val origin = origin(i++)
                val base = readExpression()
                NamedCallExpression(base, "unaryMinus", null, emptyList(), currPackage, origin)
            }
            tokens.equals(i, "++") -> createPrefixExpression(InplaceModifyType.INCREMENT, origin(i++), readExpression())
            tokens.equals(i, "--") -> createPrefixExpression(InplaceModifyType.DECREMENT, origin(i++), readExpression())
            tokens.equals(i, "*") -> {
                i++ // skip star
                ArrayToVarargsStar(readExpression())
            }
            tokens.equals(i, "::") -> {
                val origin = origin(i++)
                check(tokens.equals(i, TokenType.NAME))
                val name = tokens.toString(i++)
                // :: means a function of the current class
                DoubleColonPrefix(currPackage, name, currPackage, origin)
            }

            tokens.equals(i, "if") -> readIfBranch()
            tokens.equals(i, "else") -> throw IllegalStateException("Unexpected else at ${tokens.err(i)}")
            tokens.equals(i, "while") -> readWhileLoop(label)
            tokens.equals(i, "do") -> readDoWhileLoop(label)
            tokens.equals(i, "for") -> readForLoop(label)
            tokens.equals(i, "when") -> {
                i++
                when {
                    tokens.equals(i, TokenType.OPEN_CALL) -> readWhenWithSubject()
                    tokens.equals(i, TokenType.OPEN_BLOCK) -> readWhenWithConditions()
                    else -> throw IllegalStateException("Unexpected token after when at ${tokens.err(i)}")
                }
            }
            tokens.equals(i, "try") -> readTryCatch()
            tokens.equals(i, "return") -> readReturn(label)
            tokens.equals(i, "throw") -> ThrowExpression(origin(i++), readExpression())
            tokens.equals(i, "break") -> BreakExpression(label, currPackage, origin(i++))
            tokens.equals(i, "continue") -> ContinueExpression(label, currPackage, origin(i++))

            tokens.equals(i, "object") &&
                    tokens.equals(i + 1, TokenType.OPEN_BLOCK) -> readInlineClass0()

            tokens.equals(i, "object") &&
                    tokens.equals(i + 1, ":") -> readInlineClass()

            tokens.equals(i, TokenType.NAME) -> {
                val origin = origin(i)
                val namePath = tokens.toString(i++)
                val typeArgs = readTypeParameters(null)
                if (
                    tokens.equals(i, TokenType.OPEN_CALL) &&
                    tokens.isSameLine(i - 1, i)
                ) {
                    // constructor or function call with type args
                    val start = i
                    val end = tokens.findBlockEnd(i, TokenType.OPEN_CALL, TokenType.CLOSE_CALL)
                    if (LOGGER.enableDebug) LOGGER.debug(
                        "tokens for params: ${
                            (start..end).map { idx ->
                                "${tokens.getType(idx)}(${tokens.toString(idx)})"
                            }
                        }"
                    )
                    val args = pushCall { readParamExpressions() }
                    val base = nameExpression(namePath, origin, this, currPackage)
                    CallExpression(base, typeArgs, args, origin + 1)
                } else if (
                // todo validate that we have nothing before us...
                    tokens.equals(i, "::") && tokens.equals(i + 1, "class")) {
                    val i0 = i + 2
                    i-- // skipping over name
                    val type = readTypeNotNull(null, false)
                    check(tokens.equals(i++, "::"))
                    check(tokens.equals(i++, "class"))
                    check(i == i0)
                    GetClassFromTypeExpression(type, currPackage, origin)
                } else {
                    nameExpression(namePath, origin, this, currPackage)
                }
            }
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                // just something in brackets
                pushCall { readExpression() }
            }
            tokens.equals(i, TokenType.OPEN_BLOCK) ->
                pushBlock(ScopeType.LAMBDA, null) { readLambda() }

            else -> {
                tokens.printTokensInBlocks(max(i - 5, 0))
                throw NotImplementedError("Unknown expression part at ${tokens.err(i)}")
            }
        }
    }

    private fun readInlineClass0(): Expression {
        val origin = origin(i)
        check(tokens.equals(i++, "object"))
        check(tokens.equals(i, TokenType.OPEN_BLOCK))

        val name = currPackage.generateName("lambda")
        val clazz = currPackage.getOrPut(name, tokens.fileName, ScopeType.INLINE_CLASS)
        clazz.hasTypeParameters = true

        readClassBody(name, emptyList(), ScopeType.INLINE_CLASS)
        return ConstructorExpression(
            clazz, emptyList(), emptyList(),
            null, currPackage, origin
        )
    }

    private fun readInlineClass(): Expression {
        val origin = origin(i)
        check(tokens.equals(i++, "object"))
        check(tokens.equals(i++, ":"))

        val name = currPackage.generateName("inline")
        val clazz = currPackage.getOrPut(name, tokens.fileName, ScopeType.INLINE_CLASS)
        clazz.hasTypeParameters = true

        val bodyIndex = tokens.findToken(i, TokenType.OPEN_BLOCK)
        check(bodyIndex > i)
        push(bodyIndex) {
            while (i < tokens.size) {
                clazz.superCalls.add(readSuperCall(clazz.typeWithoutArgs))
                readComma()
            }
        }
        i = bodyIndex
        readClassBody(name, emptyList(), ScopeType.INLINE_CLASS)
        return ConstructorExpression(
            clazz, emptyList(), emptyList(),
            null, currPackage, origin
        )
    }

    private fun readSuperCall(selfType: Type): SuperCall {
        val i0 = i
        val type = readType(selfType, true) as? ClassType
            ?: throw IllegalStateException("SuperType must be a ClassType, at ${tokens.err(i0)}")

        val valueParams = if (tokens.equals(i, TokenType.OPEN_CALL)) {
            pushCall { readParamExpressions() }
        } else null

        val delegate = if (tokens.equals(i, "by")) {
            i++ // skip by
            readExpression()
        } else null

        return SuperCall(type, valueParams, delegate)
    }

    private fun readIfBranch(): IfElseBranch {
        i++
        val condition = readExpressionCondition()
        val ifTrue = readBodyOrExpression()
        val ifFalse = if (tokens.equals(i, "else") && !tokens.equals(i + 1, "->")) {
            i++
            readBodyOrExpression()
        } else null
        return IfElseBranch(condition, ifTrue, ifFalse)
    }

    private fun readWhileLoop(label: String?): WhileLoop {
        i++
        val condition = readExpressionCondition()
        val body = readBodyOrExpression()
        return WhileLoop(condition, body, label)
    }

    private fun readDoWhileLoop(label: String?): WhileLoop {
        i++
        val body = readBodyOrExpression()
        check(tokens.equals(i++, "while"))
        val condition = readExpressionCondition()
        return createWhileLoop(body = body, condition = condition, label)
    }

    private fun readForLoop(label: String?): Expression {
        i++ // skip for
        lateinit var iterable: Expression
        if (tokens.equals(i + 1, TokenType.OPEN_CALL)) {
            // destructuring expression
            val names = ArrayList<String>()
            pushCall {
                check(tokens.equals(i, TokenType.OPEN_CALL))
                pushCall {
                    while (i < tokens.size) {
                        check(tokens.equals(i, TokenType.NAME))
                        names.add(tokens.toString(i++))
                        readComma()
                    }
                }
                // to do type?
                check(tokens.equals(i++, "in"))
                iterable = readExpression()
                check(i == tokens.size)
            }
            val body = readBodyOrExpression()
            return destructuringForLoop(currPackage, names, iterable, body, label)
        } else {
            lateinit var name: String
            var variableType: Type? = null
            val origin = origin(i)
            pushCall {
                check(tokens.equals(i, TokenType.NAME))
                name = tokens.toString(i++)
                variableType = if (tokens.equals(i, ":")) {
                    i++ // skip :
                    readType(null, true)
                } else null
                // to do type?
                check(tokens.equals(i++, "in"))
                iterable = readExpression()
                check(i == tokens.size)
            }
            val body = readBodyOrExpression()
            val pseudoInitial = iterableToNextExpr(iterable)
            val variableField = Field(
                body.scope, null, true, false,
                name, variableType, pseudoInitial, emptyList(), origin
            )
            return forLoop(variableField, iterable, body, label)
        }
    }

    private fun readWhenWithSubject(): Expression {
        val subject = pushCall {
            when {
                tokens.equals(i, "val") -> readDeclaration(false, isLateinit = false)
                tokens.equals(i, "var") -> readDeclaration(true, isLateinit = false)
                else -> readExpression()
            }
        }
        check(tokens.equals(i, TokenType.OPEN_BLOCK))
        val cases = ArrayList<SubjectWhenCase>()
        val childScope = pushBlock(ScopeType.WHEN_CASES, null) { childScope ->
            while (i < tokens.size) {
                if (cases.isNotEmpty()) pushHelperScope()
                val nextArrow = findNextArrow(i)
                check(nextArrow > i)
                val conditions = readSubjectConditions(nextArrow)
                val body = readBodyOrExpression()
                if (false) {
                    LOGGER.info("next case:")
                    LOGGER.info("  condition-scope: ${currPackage.pathStr}")
                    LOGGER.info("  body-scope: ${body.scope}")
                }
                cases.add(SubjectWhenCase(conditions, currPackage, body))
            }
            childScope
        }
        return whenSubjectToIfElseChain(childScope, subject, cases)
    }

    private fun readSubjectConditions(nextArrow: Int): List<SubjectCondition?> {
        val conditions = ArrayList<SubjectCondition?>()
        if (LOGGER.enableDebug) LOGGER.debug("reading conditions, ${tokens.toString(i, nextArrow)}")
        push(nextArrow) {
            while (i < tokens.size) {
                if (LOGGER.enableDebug) LOGGER.debug("  reading condition $nextArrow,$i,${tokens.err(i)}")
                when {
                    tokens.equals(i, "else") -> {
                        i++; conditions.add(null)
                    }
                    tokens.equals(i, "in") || tokens.equals(i, "!in") -> {
                        val symbol =
                            if (tokens.toString(i++) == "in") SubjectConditionType.CONTAINS
                            else SubjectConditionType.NOT_CONTAINS
                        val value = readExpression()
                        val extra = if (tokens.equals(i, "if")) {
                            i++
                            readExpression()
                        } else null
                        conditions.add(SubjectCondition(value, null, symbol, extra))
                    }
                    tokens.equals(i, "is") || tokens.equals(i, "!is") -> {
                        val symbol =
                            if (tokens.toString(i++) == "is") SubjectConditionType.INSTANCEOF
                            else SubjectConditionType.NOT_INSTANCEOF
                        val type = readType(null, false)
                        val extra = if (tokens.equals(i, "if")) {
                            i++
                            readExpression()
                        } else null
                        conditions.add(SubjectCondition(null, type, symbol, extra))
                    }
                    else -> {
                        val value = readExpression()
                        val extra = if (tokens.equals(i, "if")) {
                            i++
                            readExpression()
                        } else null
                        conditions.add(SubjectCondition(value, null, SubjectConditionType.EQUALS, extra))
                    }
                }
                if (LOGGER.enableDebug) LOGGER.debug("  read condition '${conditions.last()}'")
                readComma()
            }
        }
        if (conditions.isEmpty()) {
            throw IllegalStateException("Missing conditions at ${tokens.err(i)}")
        }
        return conditions
    }

    private fun pushHelperScope() {
        // push a helper scope for if/else differentiation...
        val type = ScopeType.WHEN_ELSE
        val name = currPackage.generateName(type.name)
        currPackage = currPackage.getOrPut(name, type)
    }

    private fun findNextArrow(i0: Int): Int {
        var depth = 0
        var j = i0
        while (j < tokens.size) {
            when {
                tokens.equals(j, "is") ||
                        tokens.equals(j, "!is") ||
                        tokens.equals(j, "as") ||
                        tokens.equals(j, "as?") -> {
                    val originalI = i
                    i = j + 1 // after is/!is/as/as?
                    val type = readType(null, false)
                    if (LOGGER.enableDebug) LOGGER.debug("skipping over type '$type'")
                    j = i - 1 // continue after the type; -1, because will be incremented immediately after
                    i = originalI
                }
                depth == 0 && tokens.equals(j, "->") -> {
                    if (LOGGER.enableDebug) LOGGER.debug("found arrow at ${tokens.err(j)}")
                    return j
                }
                tokens.equals(j, TokenType.OPEN_BLOCK) ||
                        tokens.equals(j, TokenType.OPEN_ARRAY) ||
                        tokens.equals(j, TokenType.OPEN_CALL) -> depth++
                tokens.equals(j, TokenType.CLOSE_BLOCK) ||
                        tokens.equals(j, TokenType.CLOSE_ARRAY) ||
                        tokens.equals(j, TokenType.CLOSE_CALL) -> depth--
            }
            j++
        }
        return -1
    }

    private fun readWhenWithConditions(): Expression {
        val origin = origin(i)
        val cases = ArrayList<WhenCase>()
        pushBlock(ScopeType.WHEN_CASES, null) {
            while (i < tokens.size) {
                if (cases.isNotEmpty()) pushHelperScope()

                val nextArrow = findNextArrow(i)
                check(nextArrow > i) {
                    tokens.printTokensInBlocks(i)
                    "Missing arrow at ${tokens.err(i)} ($nextArrow vs $i)"
                }

                val condition = push(nextArrow) {
                    if (tokens.equals(i, "else")) null
                    else readExpression()
                }

                val body = readBodyOrExpression()
                cases.add(WhenCase(condition, body))
            }
        }
        return whenBranchToIfElseChain(cases, currPackage, origin)
    }

    private fun readReturn(label: String?): ReturnExpression {
        val origin = origin(i++) // skip return
        if (LOGGER.enableDebug) LOGGER.debug("reading return")
        if (i < tokens.size && tokens.isSameLine(i - 1, i) &&
            !tokens.equals(i, TokenType.COMMA)
        ) {
            val value = readExpression()
            if (LOGGER.enableDebug) LOGGER.debug("  with value $value")
            return ReturnExpression(value, label, currPackage, origin)
        } else {
            if (LOGGER.enableDebug) LOGGER.debug("  without value")
            return ReturnExpression(unitInstance, label, currPackage, origin)
        }
    }

    private fun readTryCatch(): TryCatchBlock {
        i++ // skip try
        val tryBody = readBodyOrExpression()
        val catches = ArrayList<Catch>()
        while (tokens.equals(i, "catch")) {
            check(tokens.equals(++i, TokenType.OPEN_CALL))
            val catchName = currPackage.generateName("catch")
            val catchScope = currPackage.getOrPut(catchName, ScopeType.METHOD_BODY)
            val params = pushCall { readParamDeclarations(null, catchScope) }
            check(params.size == 1)
            val handler = readBodyOrExpression()
            catches.add(Catch(params[0], handler))
        }
        val finally = if (tokens.equals(i, "finally")) {
            i++ // skip finally
            readBodyOrExpression()
        } else null
        return TryCatchBlock(tryBody, catches, finally)
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
            if (LOGGER.enableDebug) LOGGER.debug("  check ${tokens.err(i)} for type-args-compatibility")
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
                        tokens.equals(i, "val") ||
                        tokens.equals(i, "var") ||
                        tokens.equals(i, "else") ||
                        tokens.equals(i, "fun") ||
                        tokens.equals(i, "this") -> return false
                else -> throw NotImplementedError("Can ${tokens.err(i)} appear inside a type?")
            }
            i++
        }
        return true
    }

    fun readType(selfType: Type?, allowSubTypes: Boolean): Type? {
        return readType(
            selfType, allowSubTypes,
            isAndType = false, insideTypeParams = false
        )
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
        val negate = tokens.equals(i, "!")
        if (negate) i++

        var base = readTypeExpr(selfType, allowSubTypes, insideTypeParams)
            ?: run {
                i = i0 // undo any reading
                return null
            }

        if (allowSubTypes && tokens.equals(i, ".")) {
            i++
            base = readType(base, true, isAndType, insideTypeParams)
                ?: throw IllegalStateException("Expected to be able to read subtype")
            return if (negate) base.not() else base
        }

        if (tokens.equals(i, "?")) {
            i++
            base = typeOrNull(base)
        }

        if (negate) base = base.not()
        while (tokens.equals(i, "&")) {
            i++
            val typeB = readType(null, allowSubTypes, true, insideTypeParams)!!
            base = andTypes(base, typeB)
        }
        if (!isAndType && tokens.equals(i, "|")) {
            i++
            val typeB = readType(null, allowSubTypes, false, insideTypeParams)!!
            return unionTypes(base, typeB)
        }
        return base
    }

    private fun readTypeExpr(selfType: Type?, allowSubTypes: Boolean, insideTypeParams: Boolean): Type? {

        if (tokens.equals(i, "*")) {
            i++
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

        val path = readTypePath(selfType) // e.g. ArrayList
            ?: return null

        // todo We somehow need to support types like Map<K,V>.Iterator<J>, where Iterator is an inner class...
        //    or we just forbid inner classes"


        val typeArgs = readTypeParameters(selfType)
        val baseType =
            if (path is ClassType) ClassType(path.clazz, typeArgs)
            else if (typeArgs == null) path
            else throw IllegalStateException("Cannot combine $path with $typeArgs")

        if (allowSubTypes && tokens.equals(i, ".")) {
            i++ // skip ., and then read lambda/inner subtype
            val childType = readTypeNotNull(selfType, true)
            val joinedType = if (childType is LambdaType && childType.scopeType == null) {
                LambdaType(baseType, childType.parameters, childType.returnType)
            } else SubType(baseType, childType)
            return joinedType
        }

        return baseType
    }

    private fun readTypeParameters(selfType: Type?): List<Type>? {
        if (i < tokens.size) {
            if (LOGGER.enableDebug) LOGGER.debug("checking for type-args, ${tokens.err(i)}, ${isTypeArgsStartingHere(i)}")
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
            if (tokens.equals(i, "in")) i++
            if (tokens.equals(i, "out")) i++
            val type = readType(selfType, allowSubTypes = true, isAndType = false, insideTypeParams = true)
                ?: throw IllegalStateException("Expected type at ${tokens.err(i)}")
            args.add(type) // recursive type
            when {
                tokens.equals(i, TokenType.COMMA) -> i++
                tokens.equals(i, ">") -> {
                    i++ // consume '>'
                    break
                }
                else -> throw IllegalStateException("Expected , or > in type arguments, got ${tokens.err(i)}")
            }
        }
        return args
    }

    private fun <R> pushCall(readImpl: () -> R): R {
        val result = tokens.push(i++, TokenType.OPEN_CALL, TokenType.CLOSE_CALL, readImpl)
        i++ // skip )
        return result
    }

    private fun <R> pushArray(readImpl: () -> R): R {
        val result = tokens.push(i++, TokenType.OPEN_ARRAY, TokenType.CLOSE_ARRAY, readImpl)
        i++ // skip ]
        return result
    }

    fun <R> pushBlock(scopeType: ScopeType, scopeName: String?, readImpl: (Scope) -> R): R {
        val name = scopeName ?: currPackage.generateName(scopeType.name)
        return pushScope(name, scopeType) { childScope ->
            childScope.keywords.add(scopeType.name)

            val blockEnd = tokens.findBlockEnd(i++, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
            scanBlockForNewTypes(i, blockEnd)
            val result = tokens.push(blockEnd) { readImpl(childScope) }
            i++ // skip }
            result
        }
    }

    private fun <R> pushBlock(scope: Scope, readImpl: (Scope) -> R): R {
        return pushScope(scope) {
            val blockEnd = tokens.findBlockEnd(i++, TokenType.OPEN_BLOCK, TokenType.CLOSE_BLOCK)
            scanBlockForNewTypes(i, blockEnd)
            val result = tokens.push(blockEnd) { readImpl(scope) }
            i++ // skip }
            result
        }
    }

    /**
     * to make type-resolution immediately available/resolvable
     * */
    private fun scanBlockForNewTypes(i0: Int, i1: Int) {
        var depth = 0
        var listen = -1
        var listenType = ""
        var typeDepth = 0
        for (i in i0 until i1) {
            when (tokens.getType(i)) {
                TokenType.OPEN_BLOCK -> {
                    depth++
                    listen = -1
                }
                TokenType.OPEN_CALL, TokenType.OPEN_ARRAY -> depth++
                TokenType.CLOSE_CALL, TokenType.CLOSE_BLOCK, TokenType.CLOSE_ARRAY -> depth--
                else -> {
                    if (depth == 0) {
                        when {
                            tokens.equals(i, "<") -> if (listen >= 0) typeDepth++
                            tokens.equals(i, ">") -> if (listen >= 0) typeDepth--
                            // tokens.equals(i, "var") || tokens.equals(i, "val") ||
                            // tokens.equals(i, "fun") ||
                            tokens.equals(i, "class") && !tokens.equals(i - 1, "::") -> {
                                listen = i
                                listenType = "class"
                            }
                            tokens.equals(i, "object") && !tokens.equals(i + 1, ":") -> {
                                listen = i
                                listenType = "object"
                            }
                            tokens.equals(i, "interface") -> {
                                listen = i
                                listenType = "interface"
                            }
                            typeDepth == 0 && tokens.equals(i, TokenType.NAME) && listen >= 0 &&
                                    fileLevelKeywords.none { keyword -> tokens.equals(i, keyword) } -> {
                                currPackage
                                    .getOrPut(tokens.toString(i), tokens.fileName, null)
                                    .keywords.add(listenType)
                                if (LOGGER.enableDebug) LOGGER.debug("found ${tokens.toString(i)} in $currPackage")
                                listen = -1
                                listenType = ""
                            }
                        }
                    }
                }
            }
        }
        check(listen == -1) { "Listening for class/object/interface at ${tokens.err(listen)}" }
    }

    private fun <R> push(endTokenIdx: Int, readImpl: () -> R): R {
        val result = tokens.push(endTokenIdx, readImpl)
        i = endTokenIdx + 1 // skip }
        return result
    }

    fun readExpression(minPrecedence: Int = 0): Expression {
        var expr = readPrefix()
        if (LOGGER.enableDebug) LOGGER.debug("prefix: $expr")

        // main elements
        loop@ while (i < tokens.size) {
            var isInfix = false
            val symbol = when (tokens.getType(i)) {
                TokenType.SYMBOL, TokenType.KEYWORD -> tokens.toString(i)
                TokenType.NAME -> {
                    isInfix = true
                    val infix = supportedInfixFunctions.firstOrNull { infix -> tokens.equals(i, infix) }
                    // infix must be on the same line
                    if (infix == null || !tokens.isSameLine(i - 1, i)) break@loop
                    infix
                }
                TokenType.APPEND_STRING -> "+"
                else -> {
                    // postfix
                    expr = tryReadPostfix(expr) ?: break@loop
                    continue@loop
                }
            }
            when (symbol) {
                "in", "!in", "is", "!is", "+", "-" -> {
                    // these must be on the same line
                    if (!tokens.isSameLine(i - 1, i)) {
                        break@loop
                    }
                }
            }

            if (LOGGER.enableDebug) LOGGER.debug("symbol $symbol, valid? ${symbol in operators}")

            val op = operators[symbol]
            if (op == null) {
                // postfix
                expr = tryReadPostfix(expr) ?: break@loop
            } else {

                if (op.precedence < minPrecedence) break@loop

                val origin = origin(i)
                i++ // consume operator

                val scope = currPackage
                expr = when (symbol) {
                    "as" -> createCastExpression(expr, scope, origin) { ifFalseScope ->
                        val debugInfoExpr = StringExpression(expr.toString(), ifFalseScope, origin)
                        val debugInfoParam = NamedParameter(null, debugInfoExpr)
                        CallExpression(
                            MemberNameExpression("throwNPE", ifFalseScope, origin),
                            emptyList(), listOf(debugInfoParam), origin
                        )
                    }
                    "as?" -> createCastExpression(expr, scope, origin) { scope -> nullExpr(scope, origin) }
                    "is" -> {
                        val type = readTypeNotNull(null, true)
                        IsInstanceOfExpr(expr, type, scope, origin)
                    }
                    "!is" -> {
                        val type = readTypeNotNull(null, true)
                        IsInstanceOfExpr(expr, type, scope, origin).not()
                    }
                    "?:" -> createBranchExpression(
                        expr, scope, origin,
                        { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                        { fieldExpr, scope -> fieldExpr.clone(scope) }, // not null -> just the field
                        { scope -> pushScope(scope) { readExpression() } },
                    )
                    "?." -> createBranchExpression(
                        expr, scope, origin,
                        { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                        { fieldExpr, scope ->
                            pushScope(scope) {
                                handleDotOperator(
                                    fieldExpr.clone(scope),
                                    dotOperator
                                )
                            }
                        },
                        { scope -> nullExpr(scope, origin) },
                    )
                    "." -> handleDotOperator(expr, op)
                    "&&", "||" -> {
                        val left = expr
                        val name = currPackage.generateName("shortcut")
                        val right = pushScope(name, ScopeType.EXPRESSION) { readRHS(op) }
                        if (symbol == "&&") shortcutExpressionI(left, ShortcutOperator.AND, right, scope, origin)
                        else shortcutExpressionI(left, ShortcutOperator.OR, right, scope, origin)
                    }
                    "::" -> {
                        if (tokens.equals(i, "class")) {
                            when (expr) {
                                is UnresolvedFieldExpression -> {
                                    val i0 = i + 1
                                    i -= 2 // skipping over :: and name
                                    val type = readTypeNotNull(null, false)
                                    check(tokens.equals(i++, "::"))
                                    check(tokens.equals(i++, "class"))
                                    check(i == i0)
                                    GetClassFromTypeExpression(type, scope, origin)
                                }
                                else -> throw NotImplementedError("Use left side as type ($expr, ${expr.javaClass.simpleName}), then generate ::class")
                            }
                        } else {
                            val rhs = readRHS(op)
                            binaryOp(currPackage, expr, op.symbol, rhs)
                        }
                    }
                    else -> {
                        val rhs = readRHS(op)
                        if (isInfix) {
                            val param = NamedParameter(null, rhs)
                            NamedCallExpression(
                                expr, op.symbol, null,
                                listOf(param), expr.scope, origin
                            )
                        } else {
                            binaryOp(currPackage, expr, op.symbol, rhs)
                        }
                    }
                }
            }
        }

        return expr
    }

    private fun nullExpr(scope: Scope, origin: Int): SpecialValueExpression {
        return SpecialValueExpression(SpecialValue.NULL, scope, origin)
    }

    private fun isNotNullCondition(expr: Expression, scope: Scope, origin: Int): Expression {
        val nullExpr = nullExpr(scope, origin)
        return CheckEqualsOp(expr, nullExpr, false, true, scope, origin)
    }

    private fun readRHS(op: Operator): Expression =
        readExpression(if (op.assoc == Assoc.LEFT) op.precedence + 1 else op.precedence)

    private fun handleDotOperator(expr: Expression, op: Operator): Expression {
        return if (isNamedAssignment(tokens, i)) {
            val name = tokens.toString(i++)
            if (tokens.equals(i, "=")) {
                val originI = origin(i++) // skip =
                val value = readExpression()
                val nameTitlecase = name[0].uppercaseChar() + name.substring(1)
                val setterName = "set$nameTitlecase"
                val param = NamedParameter(null, value)
                NamedCallExpression(
                    expr, setterName, null,
                    listOf(param), expr.scope, originI
                )
            } else {
                // +=, -=, *=, /=, ...
                val originI = origin(i)
                val symbol = tokens.toString(i++)
                val expr1 = nameExpression(name, originI, this, currPackage)
                val left = DotExpression(expr, null, expr1, expr.scope, originI)
                val right = readExpression()
                AssignIfMutableExpr(left, symbol, right)
            }
        } else {
            val rhs = readRHS(op)
            binaryOp(currPackage, expr, op.symbol, rhs)
        }
    }

    private fun isNamedAssignment(tokens: TokenList, i: Int): Boolean {
        return tokens.equals(i, TokenType.NAME) &&
                tokens.equals(i + 1, TokenType.SYMBOL) &&
                tokens.endsWith(i + 1, '=') &&
                !tokens.equals(i + 1, "==") &&
                !tokens.equals(i + 1, "!=") &&
                !tokens.equals(i + 1, "===") &&
                !tokens.equals(i + 1, "!==")
    }

    private fun createCastExpression(
        expr: Expression, scope: Scope, origin: Int,
        ifFalseExpr: (Scope) -> Expression
    ): Expression {
        val type = readTypeNotNull(null, true)
        return createBranchExpression(expr, scope, origin, { fieldExpr ->
            IsInstanceOfExpr(fieldExpr, type, scope, origin)
        }, { fieldExpr, ifTrueScope ->
            fieldExpr.clone(ifTrueScope)
        }, ifFalseExpr)
    }

    private fun createBranchExpression(
        expr: Expression, scope: Scope, origin: Int,
        condition: (FieldExpression) -> Expression,
        ifTrueExpr: (FieldExpression, Scope) -> Expression,
        ifFalseExpr: (Scope) -> Expression,
    ): Expression {
        // we need to store the variable in a temporary field
        val tmpField = scope.generateImmutableField(expr)
        val ifTrueScope = scope.getOrPut(scope.generateName("ifTrue"), ScopeType.METHOD_BODY)
        val ifFalseScope = scope.getOrPut(scope.generateName("ifFalse"), ScopeType.METHOD_BODY)
        val fieldExpr = FieldExpression(tmpField, scope, origin)
        val condition = condition(fieldExpr)
        val ifTrueExpr = ifTrueExpr(fieldExpr, ifTrueScope)
        val ifFalseExpr = ifFalseExpr(ifFalseScope)
        return ExpressionList(
            listOf(
                AssignmentExpression(fieldExpr, expr),
                IfElseBranch(condition, ifTrueExpr, ifFalseExpr)
            ), scope, origin
        )/*.apply {
            LOGGER.info("Created branch: $this")
        }*/
    }

    private fun tryReadPostfix(expr: Expression): Expression? {
        return when {
            i >= tokens.size || !tokens.isSameLine(i - 1, i) -> null
            tokens.equals(i, TokenType.OPEN_CALL) -> {
                val origin = origin(i)
                val params = pushCall { readParamExpressions() }
                if (tokens.equals(i, TokenType.OPEN_BLOCK)) {
                    pushBlock(ScopeType.LAMBDA, null) { params += NamedParameter(null, readLambda()) }
                }
                CallExpression(expr, null, params, origin)
            }
            tokens.equals(i, TokenType.OPEN_ARRAY) -> {
                val origin = origin(i)
                val params = pushArray { readParamExpressions() }
                if (tokens.equals(i, "=")) {
                    i++ // skip =
                    val value = NamedParameter(null, readExpression())
                    NamedCallExpression(
                        expr, "set", null,
                        params + value, expr.scope, origin
                    )
                } else if (tokens.equals(i, TokenType.SYMBOL) && tokens.endsWith(i, '=')) {
                    val symbol = tokens.toString(i++)
                    val value = readExpression()
                    val call = NamedCallExpression(
                        expr, "get/set", null,
                        params, expr.scope, origin
                    )
                    AssignIfMutableExpr(call, symbol, value)
                } else {
                    NamedCallExpression(
                        expr, "get", null,
                        params, expr.scope, origin
                    )
                }
            }
            tokens.equals(i, TokenType.OPEN_BLOCK) -> {
                val origin = origin(i)
                val lambda = pushBlock(ScopeType.LAMBDA, null) { readLambda() }
                val lambdaParam = NamedParameter(null, lambda)
                CallExpression(expr, null, listOf(lambdaParam), origin)
            }
            tokens.equals(i, "++") -> createPostfixExpression(expr, InplaceModifyType.INCREMENT, origin(i++))
            tokens.equals(i, "--") -> createPostfixExpression(expr, InplaceModifyType.DECREMENT, origin(i++))
            tokens.equals(i, "!!") -> {
                val origin = origin(i++)
                val scope = currPackage
                createBranchExpression(
                    expr, scope, origin,
                    { fieldExpr -> isNotNullCondition(fieldExpr, scope, origin) },
                    { fieldExpr, ifTrueScope ->
                        fieldExpr.clone(ifTrueScope)
                    }, { scope ->
                        val debugInfoExpr = StringExpression(expr.toString(), scope, origin)
                        CallExpression(
                            MemberNameExpression("throwNPE", scope, origin), emptyList(),
                            listOf(NamedParameter(null, debugInfoExpr)), origin
                        )
                    }
                )
            }
            else -> null
        }
    }

    private fun readLambda(): Expression {
        val arrow = tokens.findToken(i, "->")
        val variables = if (arrow >= 0) {
            val variables = ArrayList<LambdaVariable>()
            tokens.push(arrow) {
                while (i < tokens.size) {
                    if (tokens.equals(i, TokenType.OPEN_CALL)) {
                        val names = ArrayList<LambdaVariable>()
                        pushCall {
                            while (i < tokens.size) {
                                if (tokens.equals(i, TokenType.NAME)) {
                                    val origin = origin(i)
                                    val name = tokens.toString(i++)
                                    val type = if (tokens.equals(i, ":")) {
                                        i++
                                        readType(null, true)
                                    } else null
                                    val parameter = LambdaVariable(type, name)
                                    names.add(parameter)
                                    // to do we neither know type nor initial value :/, both come from the called function/set variable
                                    Field( // this is more of a parameter...
                                        currPackage, null, false, parameter,
                                        name, null, null, emptyList(), origin
                                    )
                                } else throw IllegalStateException("Expected name")
                                readComma()
                            }
                        }
                        variables.add(LambdaDestructuring(names))
                    } else if (tokens.equals(i, TokenType.NAME)) {
                        val origin = origin(i)
                        val name = tokens.toString(i++)
                        val type = if (tokens.equals(i, ":")) {
                            i++
                            readType(null, true)
                        } else null
                        val parameter = LambdaVariable(type, name)
                        variables.add(parameter)
                        // to do we neither know type nor initial value :/, both come from the called function/set variable
                        Field( // this is more of a parameter...
                            currPackage, null, true, parameter,
                            name, null, null, emptyList(), origin
                        )
                    } else throw NotImplementedError()
                    readComma()
                }
            }
            i++ // skip ->
            variables
        } else null
        val body = readMethodBody()
        check(currPackage.scopeType == ScopeType.LAMBDA)
        return LambdaExpression(variables, currPackage, body)
    }

    private fun readComma() {
        if (tokens.equals(i, TokenType.COMMA)) i++
        else if (i < tokens.size) throw IllegalStateException("Expected comma at ${tokens.err(i)}")
    }

    fun readMethodBody(): ExpressionList {
        val originalScope = currPackage
        val origin = origin(i)
        val result = ArrayList<Expression>()
        if (LOGGER.enableDebug) LOGGER.debug("reading function body[$i], ${tokens.err(i)}")
        if (debug) tokens.printTokensInBlocks(i)
        while (i < tokens.size) {
            val oldSize = result.size
            val oldNumFields = currPackage.fields.size
            when {
                tokens.equals(i, TokenType.CLOSE_BLOCK) ->
                    throw IllegalStateException("} in the middle at ${tokens.err(i)}")
                tokens.equals(i, ";") -> i++ // skip
                tokens.equals(i, "@") -> annotations.add(readAnnotation())
                tokens.equals(i, "val") -> result.add(readDeclaration(false))
                tokens.equals(i, "var") -> result.add(readDeclaration(true))
                tokens.equals(i, "fun") -> {
                    // just read the method, it gets added to the scope
                    readMethod()
                }
                tokens.equals(i, "lateinit") -> {
                    check(tokens.equals(++i, "var"))
                    result.add(readDeclaration(true, isLateinit = true))
                }
                else -> {
                    result.add(readExpression())
                    if (LOGGER.enableDebug) LOGGER.debug("block += ${result.last()}")
                }
            }

            // todo check whether this works correctly
            // if expression contains assignment of any kind, or a check-call
            //  we must create a new sub-scope,
            //  because the types of our fields may have changed
            if ((result.size > oldSize && exprSplitsScope(result.last()) && i < tokens.size) ||
                currPackage.fields.size > oldNumFields
            ) {
                val newFields = currPackage.fields.subList(oldNumFields, currPackage.fields.size)
                val subName = currPackage.generateName("split")
                val newScope = currPackage.getOrPut(subName, ScopeType.METHOD_BODY)
                for (field in newFields.reversed()) {
                    field.moveToScope(newScope)
                }
                currPackage = newScope
                val remainder = readMethodBody()
                if (remainder.list.isNotEmpty()) result.add(remainder)
                // else we can skip adding it, I think
            }
        }
        val code = ExpressionList(result, originalScope, origin)
        originalScope.code.add(code)
        currPackage = originalScope // restore scope
        return code
    }

    private fun exprSplitsScope(expr: Expression): Boolean {
        return when (expr) {
            is SpecialValueExpression,
            is StringExpression,
            is NumberExpression,
            is FieldExpression,
            is MemberNameExpression,
            is UnresolvedFieldExpression,
                // todo if-else-branch can enforce a condition: if only one branch returns
            is IfElseBranch,
                // todo while-loop without break can enforce a condition, too
            is WhileLoop -> false
            is IsInstanceOfExpr -> true // all these (as, as?, is, is?) can change type information...
            is NamedCallExpression -> {
                exprSplitsScope(expr.base) ||
                        expr.valueParameters.any { exprSplitsScope(it.value) }
            }
            is DotExpression -> exprSplitsScope(expr.left) || exprSplitsScope(expr.right)
            is ReturnExpression,
            is ThrowExpression -> false // should these split the scope??? nothing after can happen
            is CallExpression -> {
                exprSplitsScope(expr.base) ||
                        expr.valueParameters.any { exprSplitsScope(it.value) } ||
                        (expr.base is MemberNameExpression && expr.base.name == "check") // this check is a little too loose
            }
            is AssignmentExpression -> true // explicit yes
            is AssignIfMutableExpr -> true // we don't know better yet
            is ExpressionList -> expr.list.any { exprSplitsScope(it) }
            is CompareOp -> exprSplitsScope(expr.value)
            is ImportedExpression -> false // I guess not...
            is LambdaExpression -> false // I don't think so
            is BreakExpression, is ContinueExpression -> false // execution ends here anyway
            is CheckEqualsOp -> exprSplitsScope(expr.left) || exprSplitsScope(expr.right)
            is DoubleColonPrefix -> false // some lambda -> no
            is NamedTypeExpression -> false
            is TryCatchBlock -> false // already a split on its own, or is it?
            else -> throw NotImplementedError("Does '$expr' (${expr.javaClass.simpleName}) split the scope (assignment / Nothing-call / ")
        }
    }

    private fun readDestructuring(isMutable: Boolean): Expression {
        val names = ArrayList<String>()
        pushCall {
            while (i < tokens.size) {
                check(tokens.equals(i, TokenType.NAME))
                names.add(tokens.toString(i++))
                if (tokens.equals(i, ":"))
                    throw NotImplementedError("Read type in destructuring at ${tokens.err(i)}")
                readComma()
            }
        }
        val value = if (tokens.equals(i, "=")) {
            i++ // skip =
            readExpression()
        } else throw IllegalStateException("Expected value for destructuring at ${tokens.err(i)}")
        return createDestructuringAssignment(names, value, isMutable)
    }

    private fun readDeclaration(isMutable: Boolean, isLateinit: Boolean = false): Expression {
        i++ // skip var/val

        if (tokens.equals(i, TokenType.OPEN_CALL)) {
            check(!isLateinit) // is immediately assigned -> cannot be lateinit
            return readDestructuring(isMutable)
        }

        check(tokens.equals(i, TokenType.NAME))
        val origin = origin(i)
        val name = tokens.toString(i++) // todo name could be path...

        if (tokens.equals(i, ".")) {
            i++
            TODO("read val Vector3d.v get() = x+y")
        }

        if (LOGGER.enableDebug) LOGGER.debug("reading var/val $name")
        val type = if (tokens.equals(i, ":")) {
            if (LOGGER.enableDebug) LOGGER.debug("skipping : for type")
            i++ // skip :
            readType(null, true).apply {
                if (LOGGER.enableDebug) LOGGER.debug("type: $this")
            }
        } else {
            if (LOGGER.enableDebug) LOGGER.debug("no type present")
            null
        }

        val value = if (tokens.equals(i, "=")) {
            i++ // skip =
            readExpression()
        } else null

        // define variable in the scope
        val field = Field(
            currPackage, getSelfType(currPackage), isMutable, null,
            name, type, value, emptyList(), origin
        )

        return DeclarationExpression(currPackage, value, field)
    }
}