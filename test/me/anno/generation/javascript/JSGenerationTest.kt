package me.anno.generation.javascript

import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.resolution.ResolutionUtils.typeResolveScope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import org.junit.jupiter.api.Test

class JSGenerationTest {

    // todo we can limit ourselves to what we need
    //  -> we really need that 'find-what-we-need' algorithm now

    @Test
    fun testJSGeneration() {
        val code = """
            val x = 1 + 2
            fun main() {
                println(x)
            }
            
            package zauber
            
            open class Any {
                external open fun toString()
            }
            
            fun println(line: Any?) = println(line?.toString() ?: "null")
            external fun println(line: String)
        """.trimIndent()
        val scope = typeResolveScope(code)
        val method = scope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        Dependencies.collectClassesAndMethods()
    }
}