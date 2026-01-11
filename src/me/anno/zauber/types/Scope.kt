package me.anno.zauber.types

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.tokenizer.TokenList
import me.anno.zauber.typeresolution.ParameterList
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

    val code = ArrayList<Expression>()

    val constructors: List<Constructor>
        get() = children.mapNotNull { it.selfAsConstructor }
    val methods: List<Method>
        get() = children.mapNotNull { it.selfAsMethod }
    val companionObject: Scope?
        get() = children.firstOrNull { it.scopeType == ScopeType.COMPANION_OBJECT }

    val fields = ArrayList<Field>()

    val superCalls = ArrayList<SuperCall>()
    val superCallNames = ArrayList<SuperCallName>()

    val enumEntries: List<Scope>
        get() = children.filter { it.scopeType == ScopeType.ENUM_ENTRY_CLASS }

    var selfAsTypeAlias: Type? = null

    var selfAsConstructor: Constructor? = null
    var selfAsMethod: Method? = null

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
    fun getOrCreatePrimConstructorScope(): Scope {
        return primaryConstructorScope ?: run {
            val scope = getOrPut("prim", ScopeType.CONSTRUCTOR)
            primaryConstructorScope = scope
            scope
        }
    }

    fun addField(field: Field) {
        val other = fields.firstOrNull {
            it.name == field.name &&
                    (it.byParameter != null) == (field.byParameter != null)
        }
        if (other != null) {
            throw IllegalStateException(
                "Each field must only be declared once per scope [$pathStr], " +
                        "${field.name} at ${TokenListIndex.resolveOrigin(field.origin)} vs ${
                            TokenListIndex.resolveOrigin(
                                other.origin
                            )
                        }"
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

    fun generate(prefix: String, scopeType: ScopeType?): Scope {
        val name = generateName(prefix)
        return getOrPut(name, scopeType)
    }

    fun getOrPut(name: String, scopeType: ScopeType?): Scope {

        // hack, because Kotlin forbids us from defining functions inside Kotlin scope
        val name = if (parent == null && name == "kotlin") "zauber" else name

        if (this.name == "Companion" && name == "ECSMeshShader")
            throw IllegalStateException("ECSMeshShader is not a part of a Companion")

        if (name == "InnerZipFile" && parent == null)
            throw IllegalStateException("Asking for $name on a global level???")

        var child = children.firstOrNull { it.name == name }
        if (child != null) {
            if (child.fileName == null) child.fileName = fileName
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
            if (child.name == name) return child
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
            if (child.name == name) return child
        }
        return null
    }

    fun resolveGenericType(name: String): Type? {
        for (param in typeParameters) {
            if (param.name == name) {
                return GenericType(this, name)
            }
        }
        for (superCall in superCalls) {
            val bySuper = superCall.type
        }
        // todo check this and any parent class for type parameters
        return null
    }

    fun resolveTypeOrNull(name: String, astBuilder: ASTBuilderBase): Type? =
        resolveTypeOrNull(name, astBuilder.imports, true)

    fun resolveTypeOrNull(
        name: String, imports: List<Import>,
        searchInside: Boolean
    ): Type? {

        // println("Resolving $name in $this ($searchInside, $fileName, ${parent?.fileName})")

        if (parent != null && parent.fileName == fileName &&
            parent.name == name
        ) return parent.typeWithoutArgs

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
                        return child.typeWithoutArgs
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
                if (child.name == name) return child.typeWithoutArgs
            }
        }

        // we must also check root for any valid paths...
        for (child in root.children) {
            if (child.name == name) {
                return child.typeWithoutArgs
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
        val name = if (name == "kotlin") "zauber" else name
        return resolveTypeOrNull(name, astBuilder)
            ?: throw IllegalStateException("Unresolved type '$name' in $this, children: ${children.map { it.name }}")
    }

    fun generateName(prefix: String): String {
        return "$$prefix${nextAnonymousName.incrementAndGet()}"
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

    fun generateImmutableField(initialValue: Expression): Field {
        val name = generateName("tmpField")
        return Field(
            this, null, false, null,
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

    companion object {
        private val nextAnonymousName = AtomicInteger(0)
    }

}