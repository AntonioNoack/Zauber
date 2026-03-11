package me.anno.zauber.scope

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Import
import me.anno.zauber.types.StandardTypes
import me.anno.zauber.types.SuperCallName
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scope / Package / Class / Object / Interface ...
 * keywords tell you what it is
 * */
class Scope(val name: String, val parent: Scope? = null) {

    var scopeType: ScopeType? = null

    var fileName: String? = parent?.fileName

    var keywords: KeywordSet = 0
    val children = ArrayList<Scope>()
    val sources = ArrayList<TokenList>()

    val code: ArrayList<Expression>
        get() = (selfAsConstructor!!.body as ExpressionList).list as ArrayList<Expression>

    val constructors: List<Constructor>
        get() = children.mapNotNull { it.scope.selfAsConstructor }
    val methods: List<Method>
        get() = children.mapNotNull { it.scope.selfAsMethod }
    val companionObject: Scope?
        get() = children
            .firstOrNull { it.scopeType == ScopeType.COMPANION_OBJECT }
            ?.scope

    val fields = ArrayList<Field>()

    val superCalls = ArrayList<SuperCall>()
    val superCallNames = ArrayList<SuperCallName>()
    val sealedPermits = ArrayList<Type>(0) // for Java

    val enumEntries: List<Scope>
        get() = children
            .filter { it.scopeType == ScopeType.ENUM_ENTRY_CLASS }
            .map { it.scope }


    val initParts = ArrayList<() -> Unit>()

    val scope: Scope
        get() {
            while (true) {
                val initializationPart = initParts.removeLastOrNull() ?: break
                initializationPart()
            }
            return this
        }

    // only one can be true, so we can store just one field, and extract everything else
    private var selfAs: Any? = null

    var selfAsTypeAlias: Type?
        get() = selfAs as? Type
        set(value) {
            selfAs = value
        }

    var selfAsConstructor: Constructor?
        get() = selfAs as? Constructor
        set(value) {
            selfAs = value
        }

    var selfAsMethod: Method?
        get() = selfAs as? Method
        set(value) {
            selfAs = value
        }

    var selfAsField: Field?
        get() = selfAs as? Field
        set(value) {
            selfAs = value
        }

    // todo register this where appropriate
    var breakLabel: String? = null

    var typeParameters: List<Parameter> = emptyList()
        set(value) {
            if (hasTypeParameters && field.size != value.size) {
                throw IllegalArgumentException("Cannot set $pathStr.typeParameters to $value, expected ${field.size} entries")
            }
            field = value
        }
    var hasTypeParameters = false

    val typeWithoutArgs = ClassType(this, null)
    val typeWithArgs by lazy {
        if (scopeType != ScopeType.PACKAGE) {
            /*check(hasTypeParameters) {
                "Missing type parameters for $pathStr"
            }*/
            // check(scopeType?.isClassType() == true)
        }
        ClassType(
            this, ParameterList(
                typeParameters,
                typeParameters.map { GenericType(it.scope, it.name) })
        )
    }

    /**
     * used for type resolution
     * */
    var imports: List<Import2> = emptyList()

    /**
     * each object Scope is also one field, and we store that here
     * */
    var objectField: Field? = null

    /**
     * for each if/else-chain, these shall be filled in
     * */
    var branchConditions: List<Expression> = emptyList()
    fun addCondition(condition: Expression) {
        if (condition in branchConditions) return
        branchConditions += condition
    }

    var primaryConstructorScope: Scope? = null
        private set

    fun getOrCreatePrimaryConstructorScope(): Scope {
        return primaryConstructorScope ?: run {
            val scope = getOrPut("prim", ScopeType.CONSTRUCTOR)
            primaryConstructorScope = scope
            scope.selfAsConstructor = Constructor(
                emptyList(), scope,
                null, ExpressionList(ArrayList(), scope, -1),
                Keywords.SYNTHETIC, -1
            )
            scope
        }
    }

    fun addField(
        selfType: Type?, // may be null inside methods (self is stack) and on package level (self is static)
        explicitSelfType: Boolean,

        isMutable: Boolean,
        byParameter: Any?, // Parameter | LambdaParameter | null

        name: String,
        valueType: Type?,
        initialValue: Expression?,
        keywords: KeywordSet,
        origin: Int
    ): Field {
        check((selfType != null) == explicitSelfType)

        val sameField = fields.firstOrNull { it.name == name && it.origin == origin }
        if (sameField != null) return sameField
        val instance = Field(
            this, selfType, explicitSelfType, isMutable, byParameter,
            name, valueType, initialValue, keywords, origin
        )
        check(instance !in fields)
        fields.add(instance)
        return instance
    }

    fun addField(field: Field) {
        val other = fields.firstOrNull { it.name == field.name }
        if (other != null) {
            throw IllegalStateException(
                "Each field must only be declared once per scope [$pathStr], ${field.name} " +
                        "at ${TokenListIndex.resolveOrigin(field.origin)} " +
                        "vs ${TokenListIndex.resolveOrigin(other.origin)}"
            )
        }
        fields.add(field)
    }

    fun ScopeType?.getClassHierarchy(): Int {
        return when (this) {
            null -> -1
            ScopeType.PACKAGE -> 0
            ScopeType.NORMAL_CLASS,
            ScopeType.ENUM_CLASS,
            ScopeType.INTERFACE,
            ScopeType.OBJECT -> 1
            ScopeType.COMPANION_OBJECT -> 2
            ScopeType.ENUM_ENTRY_CLASS -> 3
            ScopeType.INNER_CLASS -> 4
            ScopeType.CONSTRUCTOR,
            ScopeType.FIELD,
            ScopeType.FIELD_GETTER,
            ScopeType.FIELD_SETTER,
            ScopeType.INLINE_CLASS,
            ScopeType.METHOD,
            ScopeType.METHOD_BODY,
            ScopeType.LAMBDA,
            ScopeType.WHEN_CASES,
            ScopeType.WHEN_ELSE -> 6
            ScopeType.TYPE_ALIAS -> 7
        }
    }

    fun scopeHierarchyIsAllowed(self: ScopeType?, child: ScopeType?): Boolean {
        if (child == null) return true // exception for imports
        if ((self == ScopeType.METHOD_BODY || self == ScopeType.METHOD) && child == ScopeType.NORMAL_CLASS) {
            return true // exception for named classes inside methods
        }
        if (self == ScopeType.COMPANION_OBJECT &&
            child == ScopeType.COMPANION_OBJECT
        ) return false // only one is allowed
        return self.getClassHierarchy() <= child.getClassHierarchy()
    }

    fun generate(prefix: String, scopeType: ScopeType): Scope {
        val name = generateName(prefix)
        return put(name, scopeType)
    }

    fun generate(prefix: String, origin: Int, scopeType: ScopeType): Scope {
        val name = generateName(prefix, origin)
        return put(name, scopeType)
    }

    private fun langAlias(name: String): String {
        // hack, because Kotlin forbids us from defining functions inside Kotlin scope
        return if (parent == null && name == "kotlin") "zauber" else name
    }

    fun put(name: String, scopeType: ScopeType): Scope {
        val name = langAlias(name)
        check(scopeHierarchyIsAllowed(this.scopeType, scopeType)) {
            "$scopeType cannot be placed inside ${this.scopeType} ($pathStr.$name)"
        }

        val child = Scope(name, this)
        child.scopeType = scopeType
        children.add(child)
        return child
    }

    fun getOrPut(name: String, scopeType: ScopeType?): Scope {
        val name = langAlias(name)
        var child = children.firstOrNull { it.name == name }?.scope
        if (child != null) {
            // if (child.fileName == null) child.fileName = fileName
            child.mergeScopeTypes(scopeType)
            return child
        }

        check(scopeHierarchyIsAllowed(this.scopeType, scopeType)) {
            "$scopeType cannot be placed inside ${this.scopeType} ($pathStr.$name)"
        }

        child = Scope(name, this)
        child.scopeType = scopeType
        children.add(child)
        return child
    }

    fun mergeScopeTypes(scopeType: ScopeType?) {
        val self = this
        if (scopeType != null) {
            if (self.scopeType == null || self.scopeType == scopeType) self.scopeType = scopeType
            else throw IllegalStateException("ScopeType conflict! ${self.scopeType} vs $scopeType")
        }
        val parentType = parent?.scopeType
        if (!scopeHierarchyIsAllowed(parentType, scopeType)) {
            throw IllegalStateException("$scopeType cannot be placed inside $parentType} ($pathStr)")
        }
    }

    fun getOrPut(name: String, fileName: String, scopeType: ScopeType?): Scope {
        val child = getOrPut(name, scopeType)
        if (child.fileName == null) child.fileName = fileName
        return child
    }

    val path: List<String>
        get() {
            val path = ArrayList<String>()
            var that = this
            while (true) {
                if (that.name == "*") break
                path.add(that.name)
                that = that.parent!!
            }
            path.reverse()
            return path
        }

    val pathStr: String
        get() = path.joinToString(".")

    fun resolveTypeInner(name: String): Scope? {
        if (name == this.name) return this
        for (child in children) {
            if (child.name == name) return child.scope
        }

        val parent = parent
        if (parent != null && fileName == parent.fileName) {
            val byParent = parent.resolveTypeInner(name)
            if (byParent != null) return byParent
        }

        forEachSuperType { type ->
            val scope = extractScope(type)
            val bySuperCall = scope.resolveTypeInner(name)
            // println("rti[$name,$this] -> $type -> $scope -> $bySuperCall")
            if (bySuperCall != null) return bySuperCall
        }

        return null
    }

    private inline fun forEachSuperType(callback: (Type) -> Unit) {
        if (superCalls.size < superCallNames.size) {
            for (superCall in superCallNames) {
                val resolved = superCall.resolved
                if (resolved != null) {
                    callback(resolved)
                } else {
                    val type = resolveTypeOrNull(superCall.name, superCall.imports, false)
                    if (type != null) {
                        superCall.resolved = type
                        callback(type)
                    } else throw IllegalStateException("Could not resolve ${superCall.name} inside $this!")
                }
            }
        } else {
            for (superCall in superCalls) {
                callback(superCall.type)
            }
        }
    }

    private fun extractScope(type: Type): Scope {
        return when (type) {
            is ClassType -> type.clazz
            else -> throw NotImplementedError("$type")
        }
    }

    fun resolveTypeSameFolder(name: String): Scope? {
        var folderScope = this
        if (fileName == null) {
            // throw IllegalStateException("No file assigned to $this?")
            return null
        }
        while (folderScope.fileName == fileName) {
            folderScope = folderScope.parent ?: return null
        }
        // println("rtsf[$name,$this] -> $folderScope -> ${folderScope.children.map { it.name }}")
        for (child in folderScope.children) {
            if (child.name == name) return child.scope
        }
        return null
    }

    fun resolveGenericType(name: String): GenericType? {
        for (parameter in typeParameters) {
            if (parameter.name == name) {
                return GenericType(this, name)
            }
        }
        return null
    }

    fun resolveTypeOrNull(name: String, astBuilder: ASTBuilderBase): Type? =
        resolveTypeOrNull(name, astBuilder.imports, true)

    fun resolveTypeOrNull(
        name: String, imports: List<Import>,
        searchInside: Boolean
    ): Type? {

        // println("Resolving $name in $this ($searchInside, $fileName, ${parent?.fileName})")

        val parentI = parentIfSameFile
        if (parentI != null && parentI.name == name) {
            return parentI.typeWithoutArgs
        }

        if (searchInside) {
            val insideThisFile = resolveTypeInner(name)
            if (insideThisFile != null) return insideThisFile.typeWithoutArgs
        }

        val genericType = resolveGenericType(name)
        if (genericType != null) return genericType

        for (import in imports) {
            val path = import.path
            if (import.allChildren) {
                // scan all of that scope
                for (child in path.children) {
                    if (child.name == name) {
                        return child.scope.typeWithoutArgs
                    }
                }
            } else if (import.name == name) {
                return path.typeWithoutArgs
            }
        }

        val sameFolder = resolveTypeSameFolder(name)
        if (sameFolder != null) return sameFolder.typeWithoutArgs

        // helper at startup / for tests
        val standardType = StandardTypes.standardClasses[name]
        if (standardType != null) return standardType.typeWithoutArgs

        // check siblings
        if (parent != null) {
            for (child in parent.children) {
                if (child.name == name) return child.scope.typeWithoutArgs
            }
        }

        // we must also check langScope for any valid paths...
        for (child in langScope.children) {
            if (child.name == name) {
                return child.scope.typeWithoutArgs
            }
        }

        return null
    }

    fun resolveType(
        name: String, typeParameters: List<Parameter>,
        functionScope: Scope, astBuilder: ASTBuilderBase,
    ): Type {
        val typeParam = typeParameters.firstOrNull { it.name == name }
        if (typeParam != null) return GenericType(functionScope, typeParam.name)
        return resolveType(name, astBuilder)
    }

    fun resolveType(name: String, astBuilder: ASTBuilderBase): Type {
        return resolveType(name, astBuilder.imports)
    }

    fun resolveType(name: String, imports: List<Import>): Type {
        val name = if (name == "kotlin") "zauber" else name
        return resolveTypeOrNull(name, imports, true)
            ?: throw IllegalStateException("Unresolved type '$name' in $this, children: ${children.map { it.name }}")
    }

    fun resolveTypeOrNull(name: String, imports: List<Import>): Type? {
        val name = if (name == "kotlin") "zauber" else name
        return resolveTypeOrNull(name, imports, true)
    }

    @Deprecated("Please use the version with origin, if possible")
    fun generateName(prefix: String): String {
        return "$${prefix}_n${nextAnonymousName.incrementAndGet()}"
    }

    /**
     * for inner classes and methods, the origin should be that of the first class-defining keyword, e.g. 'object'
     * */
    fun generateName(prefix: String, uniqueOrigin: Int): String {
        return "$${prefix}_o$uniqueOrigin"
    }

    val parentIfSameFile: Scope?
        get() {
            val scopeType = scopeType
            return if (
                scopeType != ScopeType.PACKAGE &&
                scopeType != null
            ) {
                parent
            } else null
        }

    fun createImmutableField(initialValue: Expression): Field {
        val name = generateName("tmpField")
        return addField(
            null, false, isMutable = false, null,
            name, null, initialValue, Keywords.NONE, initialValue.origin
        )
    }

    override fun toString(): String = pathStr

    override fun equals(other: Any?): Boolean {
        return other is Scope && path == other.path
    }

    override fun hashCode(): Int {
        var hash = 1
        for (i in path.indices) {
            hash = hash * 31 + path[i].hashCode()
        }
        return hash
    }

    fun isClassType(): Boolean = scopeType?.isClassType() == true
    fun isMethodType(): Boolean = scopeType?.isMethodType() == true
    fun isTypeAlias(): Boolean = scopeType == ScopeType.TYPE_ALIAS
    fun isObject(): Boolean = scopeType?.isObject() == true
    fun isObjectLike(): Boolean = isObject() || scopeType == ScopeType.PACKAGE
    fun isInterface(): Boolean = scopeType == ScopeType.INTERFACE
    fun isValueType(): Boolean = keywords.hasFlag(Keywords.VALUE)

    fun clear() {
        // todo it would be nice to clear everything, but to do that,
        //  we need to store IntType etc on an instance level, or re-register them
        // children.clear()
        initParts.clear()
        for (child in children) {
            child.clear()
        }
        fields.clear()
        // scopeType = null
        // hasTypeParameters = false
        // typeParameters = emptyList()
        imports = emptyList()
        objectField = null
        superCalls.clear()
        superCallNames.clear()
        fileName = null
        keywords = 0
        selfAsMethod = null
        selfAsConstructor = null
        selfAsTypeAlias = null
        // todo somehow clear typeWithArgs
        // typeWithArgs
    }

    fun addKeywords(keywords: KeywordSet) {
        this.keywords = this.keywords or keywords
    }

    @Deprecated("Forcing all scopes to be loaded is overkill")
    fun forEachScope(callback: (Scope) -> Unit) {
        callback(this)
        for (i in children.indices) {
            children[i].scope.forEachScope(callback)
        }
    }

    companion object {
        private val nextAnonymousName = AtomicInteger(0)
    }

}