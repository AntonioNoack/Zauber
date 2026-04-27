package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type

// todo if it is val, we should just use simpleField
//  else, this is fine, becomes a mutable field, kind of
class SimpleDeclaration(val type: Type, val name: String, scope: Scope, origin: Int) :
    SimpleInstruction(scope, origin) {

    override fun toString(): String {
        return "DECL: $type $name;"
    }

    override fun execute(): BlockReturn? {
        // shall we do anything?
        return null
    }
}