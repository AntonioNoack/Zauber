package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.types.Scope
import me.anno.zauber.types.Type
import me.anno.zauber.types.impl.ClassType

open class MethodLike(
    open val selfType: Type?,
    val typeParameters: List<Parameter>,
    val valueParameters: List<Parameter>,
    var returnType: Type?,
    val scope: Scope,
    val body: Expression?,
    val keywords: List<String>,
    val origin: Int
) {
    var simpleBody: SimpleBlock? = null
}