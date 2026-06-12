package me.anno.cli.impl

import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.interpreting.BlockReturn
import me.anno.zauber.logging.LogManager
import kotlin.system.exitProcess

object UnitTestResults {

    private val LOGGER = LogManager.getLogger(UnitTestResults::class)

    fun showUnitTestResults(testResults: List<Pair<Method, Lazy<BlockReturn>>>) {

        val results = testResults
            .sortedBy { it.first.memberScope.pathStr }
            .map { (method, result) ->
                val result = result.value
                LOGGER.info("$method: $result")
                result.type
            }

        val statusCode = -results.count { !it.isValue() }
        exitProcess(statusCode)
    }
}