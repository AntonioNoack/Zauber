package me.anno.zauber2.stage1

import me.anno.zauber2.stage2.ResolvedScope

class LazyScope(
    val name: String,
    val type: ScopeType,
    val parent: LazyScope?,
    val tokens: TokenSubList,
) {

    // todo fields have exactly one origin
    // todo methods have exactly one origin
    // todo classes have exactly one origin
    // todo packages can have multiple origins

    val children = lazy { parseScope() }

    private fun parseScope(): ResolvedScope {
        TODO()
    }

}