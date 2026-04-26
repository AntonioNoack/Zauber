package me.anno.zauber.interpreting

import me.anno.zauber.interpreting.BasicRuntimeTests.Companion.testExecute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SuperCallTests {

    @Test
    fun testSuperCallInMethod() {
        // todo bug: this uses dynamic dispatch -> stack overflow
        val type = testExecute(
            """
                open class Parent {
                    open fun calc() = 5
                }
                class Child : Parent {
                    override fun calc() = super.calc() + 1
                }
                
                val tested = Child().calc()
                
                package zauber
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

    @Test
    fun testSuperSuperCallInMethod() {
        // todo bug: this uses dynamic dispatch -> stack overflow
        val type = testExecute(
            """
                open class GrandParent {
                    open fun calc() = 5
                }
                open class Parent: GrandParent() {
                    override fun calc() = 0
                }
                class Child : Parent() {
                    override fun calc() = super@GrandParent.calc() + 1
                }
                
                val tested = Child().calc()
                
                package zauber
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

    @Test
    fun testSuperCallInPrimaryConstructor() {
        val type = testExecute(
            """
                open class Parent(val x: Int)
                class Child(x: Int) : Parent(x+1)
                
                val tested = Child(5).x
                
                package zauber
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

    @Test
    fun testSuperCallInSecondaryConstructor() {
        val type = testExecute(
            """
                open class Parent(val x: Int)
                class Child : Parent {
                    constructor(x: Int): super(x+1)
                }
                
                val tested = Child(5).x
                
                package zauber
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }

    @Test
    fun testThisCallInSecondaryConstructor() {
        val type = testExecute(
            """
                open class Parent(val x: Int)
                class Child(x: Int) : Parent(x+1) {
                    constructor(x: Int, y: Int): this(x+y)
                }
                
                val tested = Child(2,3).x
                
                package zauber
                class Int {
                    external operator fun plus(other: Int): Int
                    operator fun inc(): Int = this + 1
                }
            """.trimIndent()
        )
        assertEquals(6, type.castToInt())
    }
}