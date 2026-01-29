package me.anno.zauber.langserver

import me.anno.zauber.langserver.logging.LoggingOutput
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.PrintStream

fun main() {
    val input = System.`in`
    val output = System.out

    // any other output is disallowed
    //  -> immediately redirect it to a log file
    System.setOut(PrintStream(LoggingOutput))
    System.setErr(PrintStream(LoggingOutput))

    val server = ZauberLanguageServer()
    val launcher = LSPLauncher.createServerLauncher(server, input, output)
    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
