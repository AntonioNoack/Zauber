package me.anno.generation.javascript

import me.anno.support.javascript.StandardLibraryLoader
import me.anno.utils.ResetThreadLocal
import org.junit.jupiter.api.Test
import java.io.File

class JavascriptGenerationTests {
    @Test
    fun testHelloWorld() {
        val home = System.getProperty("user.home")
        val files = File(home, "Downloads/lib/js")
        for (file in files.listFiles()!!) {
            ResetThreadLocal.reset()
            StandardLibraryLoader.loadStandardLibrary(file)
        }
        // todo define zauber code
        // todo automatically import all Javascript bindings from d.ts?
        // todo call into those functions :3

    }
}