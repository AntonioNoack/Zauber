package me.anno.zauber.scope

import me.anno.zauber.Compile.STDLIB_NAME
import me.anno.zauber.SpecialFieldNames.OBJECT_NAME
import me.anno.zauber.ast.FlagSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasAnyFlag
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.unresolved.LambdaExpression
import me.anno.zauber.expansion.DefaultParameters
import me.anno.zauber.expansion.EarlyTypeResolution
import me.anno.zauber.expansion.MethodOverrides
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Import
import me.anno.zauber.types.StandardTypes
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import me.anno.zauber.types.impl.SelfType
import me.anno.zauber.types.impl.ThisType
import java.util.concurrent.atomic.AtomicInteger

/**
 * Scope / Package / Class / Object / Interface ...
 * keywords tell you what it is
 * */
class Scope(val name: String, val parent: Scope? = null) {

    var scopeType: ScopeType? = null

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

    val pathStr: String = path.joinToString(".")
    val depth get() = path.size

    var fileName: String? = parent?.fileName

    var flags: FlagSet = 0
    val children = ArrayList<Scope>()
    val sources = ArrayList<TokenList>()

    val code: ArrayList<Expression>
        get() = (selfAsConstructor!!.body as ExpressionList).list as ArrayList<Expression>

    val constructors0: List<Constructor>
        get() = children.mapNotNull { it.selfAsConstructor }

    fun getConstructors(scopeInitType: ScopeInitType): List<Constructor> {
        this[scopeInitType]
        return children.mapNotNull { it[scopeInitType].selfAsConstructor }
    }

    fun getMethods(scopeInitType: ScopeInitType): List<Method> {
        this[scopeInitType]
        return children.mapNotNull { it[scopeInitType].selfAsMethod }
    }

    val methods0: List<Method>
        get() = children.mapNotNull { it.selfAsMethod }
    val companionObject: Scope?
        get() = children.firstOrNull { it.scopeType == ScopeType.COMPANION_OBJECT }

    val fields = ArrayList<Field>()

    val superCalls = ArrayList<SuperCall>()
    val sealedPermits = ArrayList<Type>(0) // for Java

    val enumEntries: List<Scope>
        get() = children
            .filter { it.scopeType == ScopeType.ENUM_ENTRY_CLASS }
            .map { it[ScopeInitType.AFTER_DISCOVERY] }

    private val initParts = ArrayList<ScopeInit>(4)
    private var sit = ScopeInitType.entries.first()

    fun addInitPart(scopeInitType: ScopeInitType, runnable: (Scope) -> Unit) {
        addInitPart(ScopeInit(scopeInitType, runnable))
    }

    fun addInitPart(scopeInit: ScopeInit) {
        // println("Adding ${scopeInit.type} to '$pathStr'")
        check(scopeInit.type >= sit) { "Cannot add ${scopeInit.type} to '$pathStr', when $sit was already queried" }
        initParts.add(scopeInit)
        initParts.sort()
    }

    init {
        addInitPart(EarlyTypeResolution.typeResolutionCreator)
        addInitPart(DefaultParameters.defaultParameterCreator)
        addInitPart(MethodOverrides.methodOverrideCreator)
    }

    operator fun get(scopeInitType: ScopeInitType): Scope {
        // println("Querying $scopeInitType in '$pathStr', stored: ${initParts.map { it.type }}")

        parent?.get(scopeInitType)

        if (scopeInitType > sit) {
            sit = scopeInitType
        }

        while (initParts.isNotEmpty() && initParts.last().type < scopeInitType) {
            @Suppress("Since15")
            val element = initParts.removeLast()
            // println("Running ${element.type} in '$pathStr'...")
            element.runnable(this)
            // println("... Finished ${element.type} in '$pathStr'")
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

    var selfAsLambda: LambdaExpression?
        get() = selfAs as? LambdaExpression
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

    @Deprecated("There is few cases, where we don't need or don't have generic parameters")
    val typeWithoutArgs = ClassType(this, null)

    private fun getParameterList(): ParameterList {
        if (!hasTypeParameters && scopeType?.needsTypeParams() != true) {
            typeParameters = emptyList()
            hasTypeParameters = true
        }
        check(hasTypeParameters) { "Missing type-params for $this ($scopeType) to take typeWithArgs" }
        return ParameterList(
            typeParameters,
            typeParameters.map { GenericType(it.scope, it.name) })
    }

    val typeWithArgs by lazy {
        ClassType(this, getParameterList())
    }

    /**
     * used for type resolution
     * */
    var imports: List<Import2> = emptyList()

    /**
     * each object Scope is also one field, and we store that here
     * */
    var objectField: Field? = null

    fun getOrCreateObjectField(origin: Int): Field {
        check(isObjectLike())
        if (objectField == null) objectField = addField(
            null, false, isMutable = false, null, OBJECT_NAME,
            ClassType(this, emptyList(), origin, true),
            /* todo should we set initialValue? */ null, Flags.NONE, origin
        )
        return objectField!!
    }

    fun getOrPutCompanion(): Scope {
        val old = companionObject
        if (old != null) return old

        val scope = getOrPut("Companion", ScopeType.COMPANION_OBJECT)
        scope.typeParameters = emptyList()
        scope.hasTypeParameters = true
        return scope
    }

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
            scope.typeParameters = emptyList()
            scope.hasTypeParameters = true

            primaryConstructorScope = scope
            scope.selfAsConstructor = Constructor(
                emptyList(), scope,
                null, ExpressionList(ArrayList(), scope, -1),
                Flags.SYNTHETIC, -1
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
        keywords: FlagSet,
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

    fun addField(field: Field): Field {
        val other = fields.firstOrNull { it.name == field.name }
        if (other != null) {
            if (other === field) return field
            throw IllegalStateException(
                "Each field must only be declared once per scope [$pathStr], ${field.name} " +
                        "at ${TokenListIndex.resolveOrigin(field.origin)} " +
                        "vs ${TokenListIndex.resolveOrigin(other.origin)}"
            )
        }
        fields.add(field)
        return field
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
            ScopeType.FIELD_GETTER,
            ScopeType.FIELD_SETTER,
            ScopeType.INLINE_CLASS,
            ScopeType.METHOD,
            ScopeType.METHOD_BODY,
            ScopeType.MACRO,
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
        if (self == ScopeType.INNER_CLASS && child == ScopeType.COMPANION_OBJECT) return true
        if (self == ScopeType.COMPANION_OBJECT && child == ScopeType.COMPANION_OBJECT) return false // only one is allowed
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
        var child = children.firstOrNull { it.name == name }
        if (child != null) {
            child[ScopeInitType.DISCOVER_MEMBERS]
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
            else throw IllegalStateException("ScopeType conflict in '$pathStr'! ${self.scopeType} vs $scopeType")
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

    fun resolveTypeInner(name: String): Scope? {
        if (name == this.name) return this
        for (child in children) {
            if (child.name == name) return child[ScopeInitType.RESOLVE_TYPES]
        }

        val parent = parent
        if (parent != null && fileName == parent.fileName) {
            val byParent = parent.resolveTypeInner(name)
            if (byParent != null) return byParent
        }

        return null
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
            if (child.name == name) return child[ScopeInitType.RESOLVE_TYPES]
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

        println("Resolving $name in $this ($searchInside, $fileName, ${parent?.fileName})")

        val parentI = parentIfSameFile
        if (parentI != null && parentI.name == name) {
            return parentI[ScopeInitType.RESOLVE_TYPES].typeWithArgs
        }

        if (searchInside) {
            val insideThisFile = resolveTypeInner(name)
            if (insideThisFile != null) return insideThisFile[ScopeInitType.RESOLVE_TYPES].typeWithArgs
        }

        val genericType = resolveGenericType(name)
        if (genericType != null) return genericType

        for (import in imports) {
            val path = import.path
            if (import.allChildren) {
                // scan all of that scope
                for (child in path.children) {
                    if (child.name == name) {
                        return child[ScopeInitType.RESOLVE_TYPES].typeWithArgs
                    }
                }
            } else if (import.name == name) {
                return path[ScopeInitType.RESOLVE_TYPES].typeWithArgs
            }
        }

        if (pathStr != STDLIB_NAME) {
            val sameFolder = resolveTypeSameFolder(name)
            if (sameFolder != null) return sameFolder[ScopeInitType.RESOLVE_TYPES].typeWithArgs
        }

        // helper at startup / for tests
        val standardType = StandardTypes.standardClasses[name]
        if (standardType != null) return standardType[ScopeInitType.RESOLVE_TYPES].typeWithArgs

        if (pathStr == STDLIB_NAME) {
            val sameFolder = resolveTypeSameFolder(name)
            if (sameFolder != null) return sameFolder[ScopeInitType.RESOLVE_TYPES].typeWithArgs
        }

        if (name == "This" && isClassLike()) {
            return ThisType(typeWithArgs)
        }

        if (name == "Self" && isClassLike()) {
            return SelfType(this)
        }

        // check siblings
        if (parent != null) {
            for (child in parent.children) {
                if (child.name == name) return child[ScopeInitType.RESOLVE_TYPES].typeWithArgs
            }
        }

        // we must also check langScope for any valid paths...
        val langScope = langScope[ScopeInitType.AFTER_DISCOVERY]
        for (child in langScope.children) {
            if (child.name == name) {
                return child[ScopeInitType.RESOLVE_TYPES].typeWithArgs
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

    val parentIfSameFileAndVisible: Scope?
        get() {
            val scopeType = scopeType
            return if (
                scopeType != ScopeType.PACKAGE &&
                scopeType != null &&
                parent != null &&
                isVisible(scopeType, parent.scopeType)
            ) {
                parent
            } else null
        }

    fun isVisible(ownScopeType: ScopeType, parentScopeType: ScopeType?): Boolean {
        if (ownScopeType == ScopeType.PACKAGE) return false
        if (parentScopeType == null || parentScopeType.isObject()) return true
        if (ownScopeType == ScopeType.INNER_CLASS) return true
        if (ownScopeType.isClassLike()) return false
        if (ownScopeType.isInsideExpression()) return true
        TODO("isVisible? $ownScopeType > $parentScopeType")
    }

    fun createImmutableField(initialValue: Expression): Field {
        val name = generateName("tmpField")
        return addField(
            null, false, isMutable = false, null,
            name, null, initialValue, Flags.NONE, initialValue.origin
        )
    }

    override fun toString(): String = pathStr

    override fun equals(other: Any?): Boolean {
        if (other is Scope) {
            val root = root
            val otherRoot = other.root
            check(root.atomic >= 0) { "Invalid root '$name', atomic is negative" }
            check(otherRoot.atomic >= 0) { "Invalid root '${otherRoot.atomic}', atomic is negative" }
            check(root === otherRoot) {
                "Root mismatch :(, " +
                        "#${root.atomic} vs " +
                        "#${otherRoot.atomic}"
            }
        }
        return other is Scope && pathStr == other.pathStr
    }

    val atomic = if (parent == null) rootIndex.incrementAndGet() else -1
    val root: Scope get() = parent?.root ?: this

    override fun hashCode(): Int {
        var hash = 1
        for (i in path.indices) {
            hash = hash * 31 + path[i].hashCode()
        }
        return hash
    }

    @Deprecated("This is unclear whether it includes objects, use isClassLike() or isClass() instead")
    fun isClassType(): Boolean = scopeType?.isClassLike() == true

    fun isClass(): Boolean = scopeType?.isClass() == true
    fun isClassOrObject() = isClass() || isObject()

    /**
     * class | enum | interface | object | package
     * aka hasInstance
     * */
    fun isClassLike(): Boolean = isClass() || isObjectLike()

    /**
     * method, getter, setter or constructor
     * */
    fun isMethodLike(): Boolean = scopeType?.isMethodLike() == true

    fun isConstructor(): Boolean = scopeType?.isConstructor() == true

    /**
     * method, getter or setter
     * */
    fun isMethod(): Boolean = scopeType?.isMethod() == true

    fun isTypeAlias(): Boolean = scopeType == ScopeType.TYPE_ALIAS
    fun isObject(): Boolean = scopeType?.isObject() ?: false
    fun isObjectLike(): Boolean = scopeType?.isObjectLike() ?: false
    fun isInterface(): Boolean = scopeType == ScopeType.INTERFACE
    fun isValueType(): Boolean = flags.hasFlag(Flags.VALUE)

    fun addFlags(flags: FlagSet) {
        this.flags = this.flags or flags
    }

    fun isInsideExpression(): Boolean {
        val scopeType = scopeType ?: return false
        return scopeType.isInsideExpression()
    }

    fun isOpen(): Boolean {
        if (isInterface()) return true
        if (isObjectLike()) return false
        return isClassType() && flags.hasAnyFlag(Flags.OPEN or Flags.ABSTRACT)
    }

    fun isInnerClassOf(ownerScope: Scope): Boolean {
        if (scopeType != ScopeType.INNER_CLASS) return false
        val parent = parent!!
        if (parent == ownerScope) return true
        return parent.isInnerClassOf(ownerScope)
    }

    companion object {
        private val nextAnonymousName = AtomicInteger(0)
        private val rootIndex = AtomicInteger(0)
    }

}