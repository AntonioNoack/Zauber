package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph

class Call(val method: MethodLike) {
    val simpleFields = HashMap<SimpleField, Instance>()

    lateinit var graph: SimpleGraph
}
