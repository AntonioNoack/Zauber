package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField

class Call(val method: MethodLike) {

    // todo we know how many there are, so we could replace this with an array
    val simpleFields = ArrayList<Instance?>()
    val localFields = ArrayList<Instance?>()

    fun setLocal(field: LocalField, instance: Instance) {
        localFields[field.id] = instance
    }

    fun setSimple(field: SimpleField, instance: Instance) {
        simpleFields[field.dst.id] = instance
    }

    lateinit var graph: SimpleGraph
}
