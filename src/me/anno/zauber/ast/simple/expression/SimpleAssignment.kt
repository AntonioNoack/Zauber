package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.scope.Scope

abstract class SimpleAssignment(var dst: SimpleField, scope: Scope, origin: Long) :
    SimpleInstruction(scope, origin)