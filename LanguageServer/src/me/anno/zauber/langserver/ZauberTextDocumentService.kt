package me.anno.zauber.langserver

import me.anno.zauber.Compile.root
import me.anno.zauber.ZauberLanguage
import me.anno.zauber.ast.rich.ZauberASTBuilder
import me.anno.zauber.langserver.logging.AppendLogging
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.types.impl.ClassType
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.TextDocumentService
import java.net.URI
import java.util.concurrent.CompletableFuture

class ZauberTextDocumentService(val zls: ZauberLanguageServer) : TextDocumentService {

    override fun didOpen(params: DidOpenTextDocumentParams) {
        val uri = params.textDocument.uri
        val text = params.textDocument.text

        val diagnostics = analyze(uri, text)
        publishDiagnostics(uri, diagnostics)
    }

    fun analyze(uri: String, text: String): List<Diagnostic> {
        AppendLogging.info("Analyzing $uri: ${text.length} chars")
        return listOf(
            Diagnostic(
                Range(
                    Position(1, 0),
                    Position(1, 1)
                ),
                "This file is ${text.length} chars long",
                DiagnosticSeverity.Hint,
                uri
            )
        )
    }

    fun publishDiagnostics(uri: String, diagnostics: List<Diagnostic>) {
        zls.client.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
    }

    override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
        val uri = params.textDocument.uri
        val fileName = uri.substringAfterLast('/')
        val src = URI(uri).toURL().readText()
        // todo we need a lenient parsing mode for the AST...
        //  if some type is not found, it's fine

        ClassType.strictMode = false

        val tokens = ZauberTokenizer(src, fileName)
            .withComments()
            .tokenize()
        val language = if (uri.endsWith(".kt")) ZauberLanguage.KOTLIN else ZauberLanguage.ZAUBER
        val ast = ZauberASTBuilder(tokens, root, language)
        try {
            ast.readFileLevel()
        } catch (e: Throwable) {
            // todo push a warning for the file...
        }
        val encoder = VSTokenEncoder(ast)
        val result = SemanticTokens(encoder.encodeTokens())
        return CompletableFuture.completedFuture(result)
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // Update AST / re-run analysis
    }

    override fun didClose(params: DidCloseTextDocumentParams) {}
    override fun didSave(params: DidSaveTextDocumentParams) {}
}
