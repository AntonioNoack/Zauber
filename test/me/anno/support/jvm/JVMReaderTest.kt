package me.anno.support.jvm

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import me.anno.zauber.logging.LogManager
import org.junit.jupiter.api.Assertions.assertEquals

fun main() {

    LogManager.disableLoggers("" +
            "MemberResolver,TypeResolver,TypeResolution," +
            "ASTSimplifier,Runtime,Inheritance," +
            "MethodResolver,CallExpression,ResolvedMethod," +
            "Field,FieldExpression,FieldResolver,FieldResolver,FieldExpression,ResolvedField," +
            "ConstructorResolver,")
    LogManager.disableLoggersCompletely("OverriddenMethods")

    // todo read a complex class like HashMap,
    //  and decode it fully into simple instructions...

    // todo first select a target class to inspect...
    //  maybe clear()

    // define some standard functions...
    testExecute(
        """
val tested = 0 // unused

package zauber
class Int {
    external operator fun plus(other: Int): Int
    external operator fun minus(other: Int): Int
    external operator fun compareTo(other: Int): Int
    external fun equals(other: Int): Boolean
}
class Array<V>(val size: Int) {
    external operator fun set(index: Int, value: V)
    external operator fun set(index: Int, value: Any)
    external operator fun get(index: Int): V
}
    """.trimIndent()
    )

    FirstJVMClassReader.getScope("java/util/ArrayList", null)
        .methods.first { it.name == "add" }
        .scope.scope

    // todo ideally, we have some jars and can just lazy-load all contents

    // try indexing some classes
    FirstJVMClassReader.getScope("java/util/ArrayList", null)
        .methods.forEach { it.scope.scope }
    FirstJVMClassReader.getScope("java/util/HashMap", null)
        .methods.forEach { it.scope.scope }

    // then try to instantiate and use an instance...
    // todo we need to fix generics... ArrayList.add() must return E, not Object
    val value = testExecute(
        """
        import java.util.ArrayList
        fun test(): Int {
            val x = ArrayList<Int>()
            x.add(1)
            return x[0]
        }
        
        val tested = test()
    """.trimIndent(), reset = false
    )
    assertEquals(1, value.castToInt())

}