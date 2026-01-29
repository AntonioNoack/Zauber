package me.anno.zauber.langserver

import me.anno.zauber.langserver.logging.AppendLogging
import me.anno.zauber.tokenizer.TokenType
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
        // todo we must create these tokens:
        //  namespace	    declare or reference a namespace, module, or package
        //  class	        declare or reference a class type
        //  enum	        declare or reference an enumeration type
        //  interface	    declare or reference an interface type
        //  struct	        declare or reference a struct type
        //  typeParameter	declare or reference a type parameter
        //  type	        declare or reference a type that is not covered above
        //  parameter	    declare or reference a function or method parameters
        //  variable	    declare or reference a local or global variable
        //  property	    declare or reference a member property, member field, or member variable
        //  enumMember	    declare or reference an enumeration property, constant, or member
        //  decorator	    declare or reference decorators and annotations
        //  event	        declare an event property
        //  function	    declare a function
        //  method	        declare a member function or method
        //  macro	        declare a macro
        //  label	        declare a label
        //  comment	        comment
        //  string	        string literal
        //  keyword	        language keyword
        //  number	        number literal
        //  regexp	        regular expression literal
        //  operator	    operator
        
        //  1) is that correct,
        //  2) determine them as far as possible...
        //     my tokens don't necessarily have these details,
        //     and I don't capture comments
        //  3) do we need the same order?

        val legend = SemanticTokensLegend(
            TokenType.entries.map {
                when (it) {
                    TokenType.KEYWORD -> "keyword"
                    TokenType.STRING -> "string"
                    TokenType.NUMBER -> "number"
                    TokenType.LINE_COMMENT,
                    TokenType.BLOCK_COMMENT -> "comment"
                    TokenType.SYMBOL -> "operator"
                    else -> it.name.lowercase()
                }
            },
            // todo we have these modifiers:
            //  declaration 	For declarations of symbols.
            //  definition	    For definitions of symbols, for example, in header files.
            //  readonly	    For readonly variables and member fields (constants).
            //  static	        For class members (static members).
            //  deprecated	    For symbols that should no longer be used.
            //  abstract	    For types and member functions that are abstract.
            //  async	        For functions that are marked async.
            //  modification	For variable references where the variable is assigned to.
            //  documentation	For occurrences of symbols in documentation.
            //  defaultLibrary	For symbols that are part of the standard library.
            emptyList() // modifiers
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
