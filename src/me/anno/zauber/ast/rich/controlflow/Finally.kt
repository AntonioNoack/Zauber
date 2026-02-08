package me.anno.zauber.ast.rich.controlflow

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Scope

class Finally(val body: Expression) {
    fun clone(scope: Scope): Finally = Finally(body.clone(scope))
    fun resolve(context: ResolutionContext): Finally = Finally(body.resolve(context))
}