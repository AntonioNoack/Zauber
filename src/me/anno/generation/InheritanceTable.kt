package me.anno.generation

import me.anno.utils.IntArrayList
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasAnyFlag
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Specialization

class InheritanceTable(val data: DependencyData) {

    companion object {
        val inheritanceCode by lazy {
            InheritanceTable::class.java
                .classLoader.getResourceAsStream("./src/IndirectCall.kt")!!
                .readBytes().decodeToString()
        }
    }

    private fun funToSpec(name: String): Specialization {
        val method = helperScope.methods0.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Missing $name() in $helperScope")
        return Specialization(method.memberScope, emptyParameterList())
    }

    val helperScope = TypeResolution.langScope
        .getOrPut("inheritance", ScopeType.PACKAGE)

    val interfaceCallSpec by lazy { funToSpec("resolveInterfaceCall") }
    val methodCallSpec by lazy { funToSpec("resolveClassCall") }

    // todo build class-call and interface-call table.
    //  class-call:
    //     instance.classIndex, functionIndex -> classCallTable[classIndex]: List<(???)->?>
    //  inheritance-call:
    //     instance.classIndex, interfaceCallIndex -> interfaceTable[classIndex] -> List<Interface>,
    //     Interface = interfaceIndex, (???)->?

    class MethodEntry(val methodIndex: Int, val method: Specialization)
    class ClassEntry(val clazz: Specialization) {
        val methods = ArrayList<MethodEntry>()
    }

    val methodToMethodIndex = HashMap<Specialization, Int>()
    val methodToInterfaceIndex = HashMap<Specialization, Int>()

    init {
        registerAllMethods()

        if (methodToMethodIndex.isNotEmpty() || methodToInterfaceIndex.isNotEmpty()) {
            data.calledMethods += interfaceCallSpec
            data.calledMethods += methodCallSpec
            data.calledMethods += funToSpec("readFromClassTable")
            data.calledMethods += funToSpec("readFromInterfaceTable")

            data.createdClasses += Specialization(helperScope, emptyParameterList())
            data.calledMethods +=
                Specialization(helperScope.getOrCreatePrimaryConstructorScope(), emptyParameterList())
        }
    }

    fun registerAllMethods() {
        // register all classes and methods, which need indirection
        for (method0 in data.calledMethods.sortedBy { getInheritanceDepth(it) }) {
            val method = method0.method as? Method ?: continue
            when {
                isInterface(method) -> registerInterfaceMethod(method0)
                isOpen(method) -> registerClassMethod(method0)
            }
        }
    }

    private fun getInheritanceDepth(method0: Specialization): Int {
        val method = method0.method as? Method ?: return 0
        var scope = method.ownerScope
        var depth = 1
        while (true) {
            scope[ScopeInitType.AFTER_RESOLVE_TYPES]
            val superCall = scope.superCalls.firstOrNull { it.isClassCall }
                ?: scope.superCalls.firstOrNull { !it.isClassCall }
                ?: return depth
            scope = superCall.type.clazz
            depth++
        }
    }

    private fun isInterface(method: Method): Boolean {
        return method.ownerScope.isInterface() &&
                !method.flags.hasFlag(Flags.FINAL)
    }

    private fun isOpen(method: Method): Boolean {
        return (method.flags.hasAnyFlag(Flags.OPEN or Flags.ABSTRACT)) ||
                (method.flags.hasFlag(Flags.OVERRIDE) && !method.flags.hasFlag(Flags.FINAL))
    }

    private fun registerInterfaceMethod(method0: Specialization) {
        registerMethod(method0, methodToInterfaceIndex)
    }

    private fun registerClassMethod(method0: Specialization) {
        registerMethod(method0, methodToMethodIndex)
    }

    private fun registerMethod(method0: Specialization, methodToMethodIndex: HashMap<Specialization, Int>) {
        var methodIndex = methodToMethodIndex[method0]
        if (methodIndex != null) return
        methodIndex = methodToMethodIndex.size

        val method = method0.method as Method
        val owner = method0.withScope(method.ownerScope)
        val children = getChildren(owner)
        val implementations = collectChildImplementations(method)
        val adapted = children.map { child ->
            val impl = implementations[child.scope!!]
                ?: throw IllegalStateException("Missing $method in $child")
            Specialization(impl.scope, method0.typeParameters + child.typeParameters)
        }
        for (child in adapted) {
            // todo only register, if child classes are known
            //  otherwise, we can just always call the method directly
            //  but register in the table anyway...
            methodToMethodIndex[child] = methodIndex
        }
    }

    private fun collectChildImplementations(method0: Method): Map<Scope, Method> {
        val result = HashMap<Scope, Method>()
        fun collect(method0: Method) {
            val owner = method0.ownerScope[ScopeInitType.ADD_OVERRIDES]
            if (result.put(owner, method0) != null) return

            for (child in method0.childMethods) {
                collect(child as Method)
            }
        }
        collect(method0)
        return result
    }

    private fun getChildren(owner: Specialization): Collection<Specialization> {
        val children = HashSet<Specialization>()
        fun addChild(child: Specialization) {
            if (children.add(child)) {
                val grandChildren = data.childClasses[child] ?: return
                for (grandChild in grandChildren) {
                    addChild(grandChild)
                }
            }
        }
        addChild(owner)
        return children
    }

    private val classes = ArrayList<Specialization>()
    private val classIndices = HashMap<Specialization, Int>()

    fun getClassCallIndex(method0: Specialization): Int {
        return methodToMethodIndex[method0]!!
    }

    fun getInterfaceCallIndex(method0: Specialization): Int {
        return methodToInterfaceIndex[method0]!!
    }

    fun buildClassTable(): IntArray {
        val builder = IntArrayList(classes.size * 3)
        repeat(classes.size) { builder.add(0) }
        for (i in classes.indices) {
            builder[i] = builder.size
            val clazz = classes[i]

            // todo collect all method indices
        }
        return builder.toIntArray()
    }

    fun buildInterfaceTable(): IntArray {
        val builder = IntArrayList(classes.size * 3)
        repeat(classes.size) { builder.add(0) }
        for (i in classes.indices) {
            builder[i] = builder.size
            val clazz = classes[i]

            // todo collect all interface indices
        }
        return builder.toIntArray()
    }
}