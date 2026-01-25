package me.anno.zauber.interpreting

import me.anno.zauber.types.Scope

class This(val instance: Instance, val scope: Scope) {
    override fun toString(): String {
        return "This(instance=$instance, scope=$scope)"
    }
}