package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleExpression

class SimpleBranch(val condition: Field, val ifTrue: List<SimpleExpression>, val ifFalse: List<SimpleExpression>)