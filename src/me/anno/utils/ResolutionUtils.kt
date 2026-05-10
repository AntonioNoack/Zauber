package me.anno.utils

import me.anno.generation.Specializations
import me.anno.zauber.Compile
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.ZauberASTClassScanner
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.scope.lazy.LazyExpression
import me.anno.zauber.tokenizer.ZauberTokenizer
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.TypeResolution
import me.anno.zauber.types.Type

object ResolutionUtils {

    private val LOGGER = LogManager.getLogger(ResolutionUtils::class)

    fun typeResolveScope(code: String, reset: Boolean = true): Scope {

        if (reset) {
            ResetThreadLocal.reset()
            Specializations.reset()
            ctr = 0
        }

        val testScopeName = "test${ctr++}"

        val sources = code
            .split("\npackage ")
            .mapIndexed { index, content ->
                if (index == 0) {
                    testScopeName to "package $testScopeName\n\n$content"
                } else {
                    var linebreak = content.indexOf('\n')
                    if (linebreak < 0) linebreak = content.length
                    val packageName = content.substring(0, linebreak).trim()
                    packageName to "package $content"
                }
            }

        for (i in sources.indices) {
            val (packageName, content) = sources[i]
            if (false) {
                println("Test.zbr")
                println(content.formatLines())
            }
            val scope = getScope(packageName)
            // sit, so we can add the parts no matter what...
            // may cause us to skip some initialization :/
            scope.addInitPart(scope.scopeInitType) {
                val tokenizer = ZauberTokenizer(content, "Test.zbr")
                ZauberASTClassScanner.scanClasses(tokenizer.tokenize())
            }
        }

        return Compile.root.children.first { it.name == testScopeName }
    }

    fun getScope(path: String): Scope {
        val parts = path.split('.')
        var scope = Compile.root
        for (part in parts) {
            check(part.trim() == part)
            scope = scope.getOrPut(part, ScopeType.PACKAGE)
        }
        return scope
    }

    fun String.formatLines(): String {
        val lines = lines()
        val pad = lines.size.toString().length
        return lines.mapIndexed { lineIndex, line ->
            "${(lineIndex + 1).toString().padStart(pad)} | $line"
        }.joinToString("\n")
    }

    operator fun Scope.get(name: String): Scope {
        return this[ScopeInitType.AFTER_DISCOVERY].children.firstOrNull { it.name == name }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${children.map { it.name }}")
    }

    fun Scope.getField(name: String): Field {
        return this[ScopeInitType.AFTER_DISCOVERY].fields.firstOrNull { it.name == name && it.byParameter == null }
            ?: throw IllegalStateException("Tried finding '$name', but only found ${fields.map { it.name }}")
    }

    fun Scope.firstChild(scopeType: ScopeType): Scope {
        return this[ScopeInitType.AFTER_DISCOVERY].children.firstOrNull { it.scopeType == scopeType }
            ?: throw IllegalStateException("Tried finding '$scopeType', but only found ${children.map { it.scopeType }}")
    }


    var ctr = 0

    fun testTypeResolution0(code: String, reset: Boolean): Scope {

        // clean slate
        if (reset) ResetThreadLocal.reset()

        val testScopeName = "test${ctr++}"
        val tokens = ZauberTokenizer(
            """
            package $testScopeName
            
            $code
        """.trimIndent(), "Test.zbr"
        ).tokenize()
        ZauberASTClassScanner.scanClasses(tokens)
        return Compile.root.children.first { it.name == testScopeName }
    }

    fun testTypeResolutionGetField(code: String, reset: Boolean): Field {
        return testTypeResolution0(code, reset).fields.first { it.name == "tested" }
    }

    fun testTypeResolution(code: String, reset: Boolean = false): Type {
        val field = testTypeResolutionGetField(code, reset)
        val context = ResolutionContext(null, false, null)
        return field.resolveValueType(context)
    }

    fun testMethodBodyResolution(code: String): List<Type> {
        val testScope = testTypeResolution0(code, reset = true)
        val method = testScope.methods0.first { it.name == "tested" }
        val types = ArrayList<Type>()
        fun scan(expr: Expression) {
            when (expr) {
                is LazyExpression -> scan(expr.value)
                is ExpressionList -> {
                    for (exprI in expr.list) {
                        scan(exprI)
                    }
                }
                else -> {
                    val context = ResolutionContext(
                        method.selfType,
                        true,
                        null,
                        emptyMap()
                    )
                    val type = TypeResolution.resolveType(context, expr)
                    types.add(type)
                }
            }
        }
        scan(method.body!!)
        return types
    }

    fun printDependencies(data: DependencyData) {
        if (!LOGGER.isInfoEnabled) return

        LOGGER.info("Classes:")
        for (clazz in data.createdClasses.map { clazz ->
            "  - ${clazz.clazz}, ${clazz.specialization}"
        }.sorted()) {
            LOGGER.info(clazz)
        }

        LOGGER.info("Methods:")
        for (method in data.calledMethods.map { method ->
            "  - ${method.method}, ${method.specialization}"
        }.sorted()) {
            LOGGER.info(method)
        }

        LOGGER.info("Fields:")
        val fields = data.getFields + data.setFields
        for (field in fields.map { field ->
            val get = field in data.getFields
            val set = field in data.setFields
            val str = when {
                !get -> "set"
                !set -> "get"
                else -> "get+set"
            }
            "  - ${field.field}, ${field.specialization}: $str"
        }.sorted()) {
            LOGGER.info(field)
        }
    }

}