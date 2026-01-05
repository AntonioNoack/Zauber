package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

class SimpleBranch(
    val condition: SimpleField, val ifTrue: SimpleBlock, val ifFalse: SimpleBlock,
    scope: Scope, origin: Int
) : SimpleExpression(scope, origin)