package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.controlflow.Flow
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Specialization

abstract class SimpleCallable(
    dst: SimpleField,
    val thisInstance: SimpleField,
    val specialization: Specialization,
    var valueParameters: List<SimpleField>,
    scope: Scope, origin: Long
) : SimpleUnsafeAssignment(dst, scope, origin) {

    val sample get() = specialization.method

    init {
        // works or crashes
        check(specialization.scope == sample.scope)
    }

    fun setValueParameter(i: Int, newField: SimpleField) {
        val valueParameters = valueParameters
        if (valueParameters is MutableList<SimpleField>) {
            valueParameters[i] = newField
        } else {
            val newValueParameters = ArrayList<SimpleField>(valueParameters)
            this.valueParameters = newValueParameters
            newValueParameters[i] = newField
        }
    }

    // todo set this, where necessary
    var onThrown: Flow? = null

}