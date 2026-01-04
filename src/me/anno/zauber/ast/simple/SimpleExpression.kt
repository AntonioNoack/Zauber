package me.anno.zauber.ast.simple

import me.anno.zauber.types.Scope

/**
 * todo implement this:
 *  there is only a few cases what it is:
 *  assignment + specifics; specifics never are a SimpleExpr
 *  return
 *  if/else/while/goto
 *  we may as well already resolve calls as far as possible :)
 * */
abstract class SimpleExpression(val scope: Scope, val origin: Int)