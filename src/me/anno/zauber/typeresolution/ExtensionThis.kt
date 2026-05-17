package me.anno.zauber.typeresolution

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

class ExtensionThis(val thisType: Type, val thisTypeToScope: Scope?, val thisField: Field) {
    override fun toString(): String {
        return "this@$thisTypeToScope"
    }
}