package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.TokenListIndex.resolveOrigin
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.scope.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

// todo if it is val, we should just use simpleField
//  else, this is fine, becomes a mutable field, kind of
open class SimpleDeclaration(val type: Type, val field: Field, scope: Scope, origin: Long) :
    SimpleInstruction(scope, origin) {

    val name get() = field.newName

    init {
        if (type is ClassType && type.clazz.isObjectLike()) {
            throw IllegalStateException("$name: $type is unnecessary at ${resolveOrigin(origin)}, skip it.")
        }
    }

    override fun toString(): String {
        return "DECL: $type $name;"
    }

    override fun execute(): BlockReturn? {
        // shall we do anything?
        return null
    }
}