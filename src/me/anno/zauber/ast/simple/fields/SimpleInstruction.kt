package me.anno.zauber.ast.simple.fields

import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.scope.Scope

/**
 * this is effectively LLVM IR, just slightly more high level
 * */
abstract class SimpleInstruction(val scope: Scope, val origin: Long) {
    abstract fun execute(): BlockReturn?

    open fun listSimpleFieldsIn(): List<SimpleField> = TODO("List in fields for ${javaClass.simpleName}")
    open fun listSimpleFieldsOut(): List<SimpleField> = TODO("List out fields for ${javaClass.simpleName}")
    open fun replaceSimpleField(old: SimpleField, new: SimpleField) {
        TODO("Replace fields for ${javaClass.simpleName}")
    }
}