package me.anno.zauber.ast.rich

import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.types.ScopeType

object ScopeSplit {

    fun ZauberASTBuilderBase.shouldSplitIntoSubScope(
        oldSize: Int,
        oldNumFields: Int,
        result: ArrayList<Expression>
    ): Boolean {
        return (result.size > oldSize && result.last().splitsScope() && i < tokens.size) ||
                currPackage.fields.size > oldNumFields
    }

    /**
     * Splits the scope, so we can re-order assignments and declarations,
     * such that
     * fun main(val x: Int) {
     *  var x = x-1
     * }
     * works.
     * */
    fun ZauberASTBuilderBase.splitIntoSubScope(oldNumFields: Int, result: ArrayList<Expression>) {
        val newFields = currPackage.fields.subList(oldNumFields, currPackage.fields.size)
        val subName = currPackage.generateName("split")
        val newScope = currPackage.getOrPut(subName, ScopeType.METHOD_BODY)
        for (field in newFields.reversed()) {
            field.moveToScope(newScope)
        }
        currPackage = newScope
        val remainder = readMethodBody()
        if (remainder.list.isNotEmpty()) result.add(remainder)
        // else we can skip adding it, I think
    }
}