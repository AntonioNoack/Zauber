package me.anno.zauber.ast.rich

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.ShortcutOperator
import me.anno.zauber.ast.rich.controlflow.shortcutExpressionI
import me.anno.zauber.ast.rich.expression.CheckEqualsOp
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.IsInstanceOfExpr
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.DotExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.ast.rich.expression.unresolved.NamedCallExpression
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types

object DataClassGenerator {

    val i31 = NumberExpression("31", root, -1)
    val i1 = NumberExpression("1", root, -1)
    val iTrue = SpecialValueExpression(SpecialValue.TRUE, root, -1)

    private const val KEYWORDS = Flags.SYNTHETIC or Flags.OVERRIDE

    class ExpressionBuilder(var scope: Scope, val origin: Int, val type: Type) {
        var expr: Expression? = null

        fun times(value: Expression) {
            expr = NamedCallExpression(expr!!, "times", value, scope, origin)
                .apply { resolvedType = type }
        }

        fun plus(value: Expression) {
            expr = NamedCallExpression(expr!!, "plus", value, scope, origin)
                .apply { resolvedType = type }
        }

        fun shortcutAnd(getCondition: (Scope) -> Expression): Scope {
            val expr = expr!!
            expr.resolvedType = Types.Boolean

            val trueScope = scope.getOrPut(scope.generateName("shortcut"), ScopeType.METHOD_BODY)
            val falseScope = scope.getOrPut(scope.generateName("shortcut"), ScopeType.METHOD_BODY)

            val condition = getCondition(trueScope)
            condition.resolvedType = Types.Boolean
            this.scope = trueScope
            this.expr = shortcutExpressionI(
                expr, ShortcutOperator.AND, condition,
                falseScope, origin
            )
            return trueScope
        }
    }

    fun Scope.findPrimaryFields(): List<Field> {
        return getOrCreatePrimaryConstructorScope()
            .selfAsConstructor!!
            .valueParameters
            .mapNotNull { it.field }
            .map { constructorField ->
                fields.first { constructorField.name == it.name }
            }
    }

    fun ZauberASTBuilderBase.finishDataClass(classScope: Scope) {

        check(currPackage === classScope) { "Expected currPackage to be classScope" }
        val origin = origin(i)

        val primaryFields = classScope.findPrimaryFields()

        val context = ResolutionContext.minimal /// todo better context?
        val hashCodeMethod = MethodResolver.findMemberInScope(
            classScope, origin, "hashCode", Types.Int, classScope.typeWithArgs,
            emptyList(), emptyList(), context
        )
        if (hashCodeMethod == null) {
            generateHashCodeMethod(classScope, origin, primaryFields)
        }

        val toStringMethod = MethodResolver.findMemberInScope(
            classScope, origin, "toString", Types.String, classScope.typeWithArgs,
            emptyList(), emptyList(), context
        )
        if (toStringMethod == null) {
            generateToStringMethod(classScope, origin, primaryFields)
        }

        val equalsAnyMethod = MethodResolver.findMemberInScope(
            classScope, origin, "equals", Types.Boolean, classScope.typeWithArgs,
            emptyList(), listOf(ValueParameterImpl(null, Types.NullableAny, false)),
            context
        )
        if (equalsAnyMethod == null) {
            generateEqualsAnyMethod(classScope, origin, primaryFields)

            // this is an optimization:
            val hasEqualsSelfMethod = MethodResolver.findMemberInScope(
                classScope, origin, "equals", Types.Boolean, classScope.typeWithArgs,
                emptyList(), listOf(ValueParameterImpl(null, classScope.typeWithArgs, false)),
                context
            ) != null
            if (!hasEqualsSelfMethod) {
                // saves type-check and cast, and therefore should be much faster,
                //  and allow us to not runtime-allocate some instances
                generateEqualsSelfMethod(classScope, origin, primaryFields)
            }
        }
    }

    private fun ZauberASTBuilderBase.generateHashCodeMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        val methodScope = pushScope("hashCode", ScopeType.METHOD) { scope ->
            scope.typeParameters = emptyList()
            scope.hasTypeParameters = true

            val builder = ExpressionBuilder(scope, origin, Types.Int)
            for (field in primaryFields) {
                val fieldExpr = FieldExpression(field, scope, origin)
                val hashExpr = NamedCallExpression(fieldExpr, "hashCode", scope, origin)
                if (builder.expr == null) {
                    builder.expr = hashExpr
                } else {
                    builder.times(i31)
                    builder.plus(hashExpr)
                }
            }
            body = ReturnExpression(builder.expr ?: i1, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, false, "hashCode", emptyList(), emptyList(),
            methodScope, Types.Int, emptyList(), body, KEYWORDS, origin
        )
    }

    private fun ZauberASTBuilderBase.generateToStringMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        val methodScope = pushScope("toString", ScopeType.METHOD) { scope ->
            scope.typeParameters = emptyList()
            scope.hasTypeParameters = true

            val builder = ExpressionBuilder(scope, origin, Types.String)
            builder.expr = StringExpression("${classScope.name}(", scope, origin)
            for ((i, field) in primaryFields.withIndex()) {
                val fieldExpr = FieldExpression(field, scope, origin)
                val stringExpr = NamedCallExpression(fieldExpr, "toString", scope, origin)
                val name = field.name
                builder.plus(StringExpression(if (i > 0) ",$name=" else "$name=", scope, origin))
                builder.plus(stringExpr)
            }
            builder.plus(StringExpression(")", scope, origin))
            body = ReturnExpression(builder.expr!!, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, false, "toString", emptyList(), emptyList(),
            methodScope, Types.String, emptyList(), body, KEYWORDS, origin
        )
    }

    private fun ZauberASTBuilderBase.generateEqualsAnyMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        lateinit var parameter: Parameter
        val methodScope = pushScope("equals", ScopeType.METHOD) { scope ->
            scope.typeParameters = emptyList()
            scope.hasTypeParameters = true

            parameter = Parameter(0, "other", Types.NullableAny, scope, origin)
            val otherField = parameter.getOrCreateField(null, Flags.NONE)

            val builder = ExpressionBuilder(scope, origin, Types.Boolean)
            val otherInstanceExpr0 = FieldExpression(otherField, scope, origin)
            builder.expr = IsInstanceOfExpr(otherInstanceExpr0, classScope.typeWithArgs, scope, origin)

            for (field in primaryFields) {
                builder.shortcutAnd { subScope ->
                    val fieldExpr = FieldExpression(field, subScope, origin)
                    val otherInstanceI = FieldExpression(otherField, subScope, origin) // must be here for implicit cast
                    val otherFieldExpr = DotExpression(otherInstanceI, emptyList(), fieldExpr, subScope, origin)
                    CheckEqualsOp(
                        fieldExpr, otherFieldExpr,
                        byPointer = false, negated = false,
                        null, subScope, origin
                    )
                }
            }

            body = ReturnExpression(builder.expr ?: iTrue, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, false, "equals", emptyList(), listOf(parameter),
            methodScope, Types.Boolean, emptyList(), body, KEYWORDS, origin
        )
    }

    private fun ZauberASTBuilderBase.generateEqualsSelfMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        lateinit var parameter: Parameter
        val methodScope = pushScope("equals", ScopeType.METHOD) { scope ->
            scope.typeParameters = emptyList()
            scope.hasTypeParameters = true

            parameter = Parameter(0, "other", classScope.typeWithArgs, scope, origin)
            val otherField = parameter.getOrCreateField(null, Flags.NONE)

            val builder = ExpressionBuilder(scope, origin, Types.Boolean)
            for (field in primaryFields) {
                builder.shortcutAnd { subScope ->
                    val fieldExpr = FieldExpression(field, subScope, origin)
                    val otherInstanceI = FieldExpression(otherField, subScope, origin) // must be here for implicit cast
                    val otherFieldExpr = DotExpression(otherInstanceI, emptyList(), fieldExpr, subScope, origin)
                    CheckEqualsOp(
                        fieldExpr, otherFieldExpr,
                        byPointer = false, negated = false,
                        null, subScope, origin
                    )
                }
            }

            body = ReturnExpression(builder.expr ?: iTrue, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, false, "equals", emptyList(), listOf(parameter),
            methodScope, Types.Boolean, emptyList(), body, KEYWORDS, origin
        )
    }

}