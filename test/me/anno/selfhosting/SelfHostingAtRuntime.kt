package me.anno.selfhosting

import me.anno.cli.ZauberCLI
import me.anno.libraries.Library.Companion.PROJECT_FILE_NAME
import org.junit.jupiter.api.Test
import java.io.File

// todo - to run this, we need an extensive standard library...
//   -> we could load the Kotlin standard library, ...

// todo - run the compiler within itself
// todo - compile a small program

class SelfHostingAtRuntime {
    @Test
    fun runCompilerWithinRuntime() {
        File(PROJECT_FILE_NAME)
            .writeText(
                """
                [package]
                name="Zauber"
                version="0.0.1"
                source="src"
                
                [dependencies]
                stdlib={source="Samples"}
            """.trimIndent()
            )
        ZauberCLI.main(arrayOf("run"))
    }
}