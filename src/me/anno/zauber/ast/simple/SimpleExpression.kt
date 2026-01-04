package me.anno.zauber.ast.simple

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.types.Scope

/**
 * todo implement this:
 *  there is only a few cases what it is:
 *  assignment + specifics; specifics never are a SimpleExpr
 * */
abstract class SimpleExpression(val dst: Field, val scope: Scope, val origin: Int)