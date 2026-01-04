package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleExpression
import me.anno.zauber.types.Scope

class SimpleLabel(val name: String, scope: Scope, origin: Int) : SimpleExpression(scope, origin)