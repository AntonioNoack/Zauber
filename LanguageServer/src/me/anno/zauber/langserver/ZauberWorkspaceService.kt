package me.anno.zauber.langserver

import me.anno.zauber.langserver.logging.AppendLogging
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.services.WorkspaceService

class ZauberWorkspaceService(val zls: ZauberLanguageServer) : WorkspaceService {
    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
        AppendLogging.info("Found changed configuration")
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {
        AppendLogging.info("Found changed files")
    }
}
