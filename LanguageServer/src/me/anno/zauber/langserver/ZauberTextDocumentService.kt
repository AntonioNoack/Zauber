package me.anno.zauber.langserver

import me.anno.generation.Specializations
import me.anno.support.Language
import me.anno.utils.ResetThreadLocal
import me.anno.zauber.ast.rich.parser.SemanticTokenList
import me.anno.zauber.ast.rich.parser.ZauberASTClassScanner
import me.anno.zauber.langserver.logging.AppendLogging
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.types.impl.ClassType
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.TextDocumentService
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * analyzes the code, and calculates semantic token types
 * */
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

        // todo instead of resetting everything, it would be nice if we could cache some data,
        //  and just discard data inside previously read scopes...
        ResetThreadLocal.reset()
        Specializations.reset()
        ClassType.strictMode = false

        val tokens = ZauberTokenizer(src, fileName)
            .withComments()
            .tokenize()

        val semantic = SemanticTokenList(tokens)
        tokens.semantic = semantic

        val language = if (uri.endsWith(".kt")) Language.KOTLIN else Language.ZAUBER
        val ast = ZauberASTClassScanner(tokens, language)
        try {
            ast.readFileLevel()
            discoverLazyLevels(ast.currPackage) // a little hacky
        } catch (e: Throwable) {
            // todo push a warning for the file...
        }

        val encoder = VSTokenEncoder(tokens, semantic)
        val result = SemanticTokens(encoder.encodeTokens())
        return CompletableFuture.completedFuture(result)
    }

    fun discoverLazyLevels(scope: Scope) {
        scope[ScopeInitType.DISCOVER_MEMBERS]

        val body = scope.selfAsMethod?.body
            ?: scope.selfAsConstructor?.body
            ?: scope.selfAsLambda
        if (body is LazyExpression) {
            body.value
        }

        for (child in scope.children) {
            discoverLazyLevels(child)
        }
    }

    override fun didChange(params: DidChangeTextDocumentParams) {
        // Update AST / re-run analysis
    }

    override fun didClose(params: DidCloseTextDocumentParams) {}
    override fun didSave(params: DidSaveTextDocumentParams) {}
}
