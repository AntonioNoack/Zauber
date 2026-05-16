package me.anno.zauber.interpreting

import me.anno.MultiTest
import me.anno.generation.LoggerUtils.disableCompileLoggers
import me.anno.utils.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PackageAsFieldTest {

    private val stdlib = "\n" + """
package zauber
class Any
class Int
object Unit
external fun println(arg0: Int)
    """.trimIndent()

    @ParameterizedTest
    @ValueSource(strings = ["runtime", "js", "java", "c++", "wasm", "rust"])
    fun testPackageAsField(type: String) {
        disableCompileLoggers()
        val code = """
            fun calculate(): Int {
                var tmp = helper020
                return tmp.x
            }
            
            val tested = calculate()
            
            package helper020
            var x = 5
        """.trimIndent() + stdlib
        MultiTest()
            .runtime(code) {
                assertEquals(5, it.castToInt())
            }
            .compile(code, "5\n")
            .execute(type)
    }

    @ParameterizedTest
    @ValueSource(strings = ["runtime", "js", "java", "c++", "wasm", "rust"])
    fun testNestedPackageAsField(type: String) {
        disableCompileLoggers()
        val code = """
            fun calculate(): Int {
                var leSub = helper020.sub
                return leSub.x
            }
            
            val tested = calculate()
            
            package helper020.sub
            var x = 5
        """.trimIndent() + stdlib
        MultiTest()
            .runtime(code) {
                assertEquals(5, it.castToInt())
            }
            .compile(code, "5\n")
            .execute(type)
    }
}