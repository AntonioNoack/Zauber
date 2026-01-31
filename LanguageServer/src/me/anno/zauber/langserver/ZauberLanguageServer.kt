package me.anno.zauber.langserver

import me.anno.langserver.VSCodeModifier
import me.anno.langserver.VSCodeType
import me.anno.zauber.langserver.logging.AppendLogging
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import java.util.concurrent.CompletableFuture

class ZauberLanguageServer : LanguageServer, LanguageClientAware {

    lateinit var client: LanguageClient
    private val textDocumentService = ZauberTextDocumentService(this)
    private val workspaceService = ZauberWorkspaceService(this)

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        AppendLogging.info("initialize")

        // https://code.visualstudio.com/api/language-extensions/semantic-highlight-guide
        // todo we must create these tokens
        val legend = SemanticTokensLegend(
            VSCodeType.entries.map { it.code },
            VSCodeModifier.entries.map { it.code }
        )

        val capabilities = ServerCapabilities().apply {
            semanticTokensProvider = SemanticTokensWithRegistrationOptions().apply {
                this.legend = legend
                this.full = Either.forLeft(true)
            }
            textDocumentSync = Either.forLeft(TextDocumentSyncKind.Incremental)
            completionProvider = CompletionOptions()
        }
        return CompletableFuture.completedFuture(InitializeResult(capabilities))
    }

    override fun shutdown(): CompletableFuture<Any> =
        CompletableFuture.completedFuture(null)

    override fun exit() {}

    override fun getTextDocumentService() = textDocumentService
    override fun getWorkspaceService() = workspaceService

    override fun connect(client: LanguageClient) {
        AppendLogging.info("connected")
        this.client = client
    }
}
