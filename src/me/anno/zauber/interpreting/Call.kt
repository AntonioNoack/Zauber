package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.SimpleField

class Call(val method: MethodLike, val self: Instance, val valueParameters: List<Instance>) {
    val fields = HashMap<SimpleField, Instance>()
    var returnValue: Instance? = null
}
