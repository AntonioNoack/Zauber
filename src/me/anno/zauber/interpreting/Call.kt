package me.anno.zauber.interpreting

import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.scope.Scope

class Call(val method: MethodLike) {
    val simpleFields = HashMap<SimpleField, Instance>()
    val scopes = HashMap<Scope, Instance>()

    lateinit var graph: SimpleGraph
}
