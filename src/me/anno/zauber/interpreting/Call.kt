package me.anno.zauber.interpreting

import me.anno.zauber.ast.simple.SimpleField

class Call(val self: Instance, val valueParameters: List<Instance>) {
    val fields = HashMap<SimpleField, Instance>()
    var returnValue: Instance? = null
}
