package me.anno.selfhosting

import me.anno.cli.ZauberCLI
import me.anno.libraries.Library.Companion.PROJECT_FILE_NAME
import org.junit.jupiter.api.Test
import java.io.File

// todo - compile this into LLVM
// todo - compile sample program (or self) using LLVM

class SelfHostingInLLVM {
    @Test
    fun runCompilerInLLVM() {
        File(PROJECT_FILE_NAME)
            .writeText(
                """
                [package]
                name="Zauber"
                version="0.0.1"
                source="src"
            """.trimIndent()
            )
        ZauberCLI.main(arrayOf("run", "--target", "llvm"))
    }
}