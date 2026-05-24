package me.anno.generation

import me.anno.utils.IntArrayList
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.Specialization

class InheritanceTable(val data: DependencyData) {

    // todo build class-call and interface-call table.
    //  class-call:
    //     instance.classIndex, functionIndex -> classCallTable[classIndex]: List<(???)->?>
    //  inheritance-call:
    //     instance.classIndex, interfaceCallIndex -> interfaceTable[classIndex] -> List<Interface>,
    //     Interface = interfaceIndex, (???)->?

    init {
        // todo register all classes and methods, which need indirection

    }

    private val classes = ArrayList<Specialization>()
    private val classIndices = HashMap<Specialization, Int>()

    fun getClassIndex(spec: Specialization): Int {
        return classIndices.getOrPut(spec) {
            classes.add(spec)
            classIndices.size
        }
    }

    fun getClassCallIndex(method0: Specialization): Int {
        val method = method0.method
        method.memberScope[ScopeInitType.AFTER_OVERRIDES]

        if (method.overriddenFor.isNotEmpty()) {
            val superMethod = method.overriddenFor.first()
            return getClassCallIndex(method0.withScope(superMethod.memberScope))
        }

        // todo if method is override, find super-method-index instead
        // todo all child classes must be registered
        TODO("Register $method/$method0")
    }

    fun getInterfaceCallIndex(method0: Specialization): Int {
        // if class is override, find super-method-index instead?
        // todo all child classes must be registered
        TODO()
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