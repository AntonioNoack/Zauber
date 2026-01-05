package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.ast.simple.SimpleField
import me.anno.zauber.types.Scope

abstract class SimpleAssignmentExpression(val dst: SimpleField, scope: Scope, origin: Int) :
    SimpleExpression(scope, origin)