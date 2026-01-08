package me.anno.zauber.ast.simple

import me.anno.zauber.interpreting.Runtime
import me.anno.zauber.types.Scope

/**
 * todo implement this:
 *  there is only a few cases what it is:
 *  assignment + specifics; specifics never are a SimpleExpr
 *  return
 *  if/else/while/goto
 *  we may as well already resolve calls as far as possible :)
 *
 * todo this is effectively LLVM IR, just that names can be used multiple times
 * */
abstract class SimpleExpression(val scope: Scope, val origin: Int) {
    abstract fun execute(runtime: Runtime)
}