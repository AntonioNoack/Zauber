package me.anno.zauber.ast.simple.expression

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleExpression

class SimpleDeclaration(val field: Field) : SimpleExpression(field.declaredScope, field.origin)