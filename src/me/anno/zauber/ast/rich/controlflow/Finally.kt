package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope

class Finally(val body: Expression, val flag: Field) {
    fun clone(scope: Scope): Finally = Finally(body.clone(scope), flag)
    fun resolve(context: ResolutionContext): Finally = Finally(body.resolve(context), flag)
}