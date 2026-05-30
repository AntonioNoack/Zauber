package me.anno.compilation

import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.interpreting.ReturnType
import me.anno.zauber.interpreting.Runtime.Companion.runtime
import me.anno.zauber.interpreting.Stdlib.registerAllMethods
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.TypeResolution.langScope
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types
import java.io.File

/**
 * Not really a compiler, it just behaves like the others.
 * Interprets the main() method, and returns the printed text.
 * */
class RuntimeCompiler : MinimalCompiler() {

    private lateinit var data: DependencyData
    private lateinit var mainMethod: Method

    override fun compile(
        projectFolder: File,
        srcFolder: File,
        dependencies: DependencyData,
        mainMethod: Method
    ) {
        this.data = dependencies
        this.mainMethod = mainMethod
    }

    override fun execute(projectFolder: File): String {
        val runtime = runtime

        registerAllMethods() // register testing methods

        // register printing to buffer, so we can keep track of it
        val buffer = StringBuilder()
        runtime.register(langScope, "println", listOf(Types.Int)) { _, params ->
            buffer.append(params[0].castToInt()).append('\n')
            runtime.getUnit()
        }

        val main = mainMethod
        val self = runtime.getObjectInstance(main.ownerScope.typeWithArgs)
        val spec = Specialization(main.memberScope, emptyParameterList())
        val result = runtime.executeCall(self, null, spec, emptyList())
        check(result.type == ReturnType.RETURN) { "Call failed: $result" }
        return buffer.toString()
    }
}