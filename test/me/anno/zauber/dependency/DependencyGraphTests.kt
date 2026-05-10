package me.anno.zauber.dependency

import me.anno.utils.ResolutionUtils
import me.anno.utils.ResolutionUtils.printDependencies
import me.anno.zauber.Compile
import me.anno.zauber.expansion.Dependencies
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Types
import me.anno.zauber.types.specialization.ClassSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DependencyGraphTests {

    // todo we should also define a more complex example,
    //  e.g. with generics

    @Test
    fun testSimpleDependencyGraph() {
        val code = """
            val x = 1 + 2
            fun main() {
                println(x)
            }
            
            package zauber
            class Int {
                external operator fun plus(other: Int): Int
            }
            
            external fun println(line: Int)
        """.trimIndent()
        val testScope = ResolutionUtils.typeResolveScope(code)
        val method = testScope[ScopeInitType.AFTER_DISCOVERY].methods0.first { it.name == "main" }
        Dependencies.addMethod(MethodSpecialization(method, Specialization.noSpecialization))

        val dependencies = Dependencies.collectClassesAndMethods()
        printDependencies(dependencies)
        val classes = dependencies.createdClasses
        val methods = dependencies.calledMethods

        val zauberScope = Compile.root.getOrPut("zauber", ScopeType.PACKAGE)
        val printlnMethod = zauberScope
            .methods0.first { it.name == "println" }
        val intPlusMethod = Types.Int.clazz
            .methods0.first { it.name == "plus" }

        // validate with what we expect
        val expectedClasses = listOf(
            Types.Any,
            Types.Int,
            Types.Unit,
            zauberScope.typeWithArgs,
            testScope.typeWithArgs,
        ).map { ClassSpecialization(it) }
        assertEquals(expectedClasses.toSet(), classes)

        val expectedMethods = setOf(
            method, printlnMethod, intPlusMethod,
            // zauberScope.primaryConstructorScope!!.selfAsConstructor!!,
            testScope.primaryConstructorScope!!.selfAsConstructor!!,
        )
            .map { method -> MethodSpecialization(method, Specialization.noSpecialization) }
            .toSet()
        assertEquals(expectedMethods, methods)
    }
}