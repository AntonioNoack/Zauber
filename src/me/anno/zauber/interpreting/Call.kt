package me.anno.zauber.interpreting

import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class Call(val self: Instance) {
    val simpleFields = HashMap<SimpleField, Instance>()
    val scopes = HashMap<Scope, Instance>()

    var returnValue: Instance? = null
}
