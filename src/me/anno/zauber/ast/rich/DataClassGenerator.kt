package me.anno.zauber.ast.rich

import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.ZauberASTBuilder.Companion.synthetic
import me.anno.zauber.ast.rich.controlflow.ReturnExpression
import me.anno.zauber.ast.rich.controlflow.shortcutExpressionI
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.MethodResolver
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.StringType

object DataClassGenerator {

    val i31 = NumberExpression("31", root, -1)
    val i1 = NumberExpression("1", root, -1)

    private val keywords = listOf(synthetic, "override")

    class ExpressionBuilder(var scope: Scope, val origin: Int, val type: Type) {
        var expr: Expression? = null

        fun times(value: Expression) {
            expr = NamedCallExpression(
                expr!!, "times", emptyList(),
                listOf(NamedParameter(null, value)), scope, origin
            ).apply { resolvedType = type }
        }

        fun plus(value: Expression) {
            expr = NamedCallExpression(
                expr!!, "plus", emptyList(),
                listOf(NamedParameter(null, value)), scope, origin
            ).apply { resolvedType = type }
        }

        fun shortcutAnd(getCondition: (Scope) -> Expression): Scope {
            val expr = expr!!
            expr.resolvedType = BooleanType

            val trueScope = scope.getOrPut(scope.generateName("shortcut"), ScopeType.EXPRESSION)
            val falseScope = scope.getOrPut(scope.generateName("shortcut"), ScopeType.EXPRESSION)

            val condition = getCondition(trueScope)
            condition.resolvedType = BooleanType
            this.scope = trueScope
            this.expr = shortcutExpressionI(
                expr, ShortcutOperator.AND, condition,
                falseScope, origin
            )
            return trueScope
        }
    }

    fun ZauberASTBuilder.finishDataClass(classScope: Scope) {

        check(currPackage === classScope)
        val origin = origin(i)

        val primaryFields = classScope.getOrCreatePrimConstructorScope()
            .selfAsConstructor!!
            .valueParameters
            .mapNotNull { it.field }

        val hashCodeMethod = MethodResolver.findMemberInScope(
            classScope, origin, "hashCode", IntType, classScope.typeWithoutArgs,
            emptyList(), emptyList()
        )
        if (hashCodeMethod == null) {
            generateHashCodeMethod(classScope, origin, primaryFields)
        }

        val toStringMethod = MethodResolver.findMemberInScope(
            classScope, origin, "toString", StringType, classScope.typeWithoutArgs,
            emptyList(), emptyList()
        )
        if (toStringMethod == null) {
            generateToStringMethod(classScope, origin, primaryFields)
        }

        val equalsAnyMethod = MethodResolver.findMemberInScope(
            classScope, origin, "equals", BooleanType, classScope.typeWithoutArgs,
            emptyList(), listOf(ValueParameterImpl(null, NullableAnyType, false))
        )
        if (equalsAnyMethod == null) {
            generateEqualsAnyMethod(classScope, origin, primaryFields)

            // this is an optimization:
            val hasEqualsSelfMethod = MethodResolver.findMemberInScope(
                classScope, origin, "equals", BooleanType, classScope.typeWithoutArgs,
                emptyList(), listOf(ValueParameterImpl(null, classScope.typeWithoutArgs, false))
            ) != null
            if (!hasEqualsSelfMethod) {
                // todo generate this method?
            }
        }
    }

    private fun ZauberASTBuilder.generateHashCodeMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        val methodScope = pushScope("hashCode", ScopeType.METHOD) { scope ->
            val builder = ExpressionBuilder(scope, origin, IntType)
            for (field in primaryFields) {
                val fieldExpr = FieldExpression(field, scope, origin)
                val hashExpr = NamedCallExpression(
                    fieldExpr, "hashCode",
                    emptyList(), emptyList(), scope, origin
                )
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
            classScope.typeWithArgs, "hashCode", emptyList(), emptyList(),
            methodScope, IntType, emptyList(), body, DataClassGenerator.keywords, origin
        )
    }

    private fun ZauberASTBuilder.generateToStringMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        val methodScope = pushScope("toString", ScopeType.METHOD) { scope ->
            val builder = ExpressionBuilder(scope, origin, StringType)
            builder.expr = StringExpression("${classScope.name}(", scope, origin)
            for ((i, field) in primaryFields.withIndex()) {
                val fieldExpr = FieldExpression(field, scope, origin)
                val hashExpr = NamedCallExpression(
                    fieldExpr, "toString",
                    emptyList(), emptyList(), scope, origin
                )
                val name = field.name
                builder.plus(StringExpression(if (i > 0) ",$name=" else "$name=", scope, origin))
                builder.plus(hashExpr)
            }
            builder.plus(StringExpression(")", scope, origin))
            body = ReturnExpression(builder.expr!!, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, "toString", emptyList(), emptyList(),
            methodScope, StringType, emptyList(), body, DataClassGenerator.keywords, origin
        )
    }

    private fun ZauberASTBuilder.generateEqualsAnyMethod(
        classScope: Scope, origin: Int,
        primaryFields: List<Field>
    ) {
        lateinit var body: Expression
        lateinit var parameter: Parameter
        val methodScope = pushScope("equals", ScopeType.METHOD) { scope ->
            parameter = Parameter("other", NullableAnyType, scope, origin)
            val otherField = Field(
                scope, null, false, parameter,
                parameter.name, NullableAnyType, null, emptyList(), origin
            )
            parameter.field = otherField
            val otherInstanceExpr = FieldExpression(otherField, scope, origin)

            val builder = ExpressionBuilder(scope, origin, BooleanType)
            builder.expr = IsInstanceOfExpr(otherInstanceExpr, classScope.typeWithArgs, scope, origin)

            for (field in primaryFields) {
                builder.shortcutAnd { subScope ->
                    val fieldExpr = FieldExpression(field, subScope, origin)
                    val otherFieldExpr = DotExpression(otherInstanceExpr, emptyList(), fieldExpr, subScope, origin)
                    CheckEqualsOp(
                        fieldExpr, otherFieldExpr,
                        byPointer = false, negated = false,
                        subScope, origin
                    )
                }
            }

            body = ReturnExpression(builder.expr ?: i1, null, scope, origin)
            scope
        }
        methodScope.selfAsMethod = Method(
            classScope.typeWithArgs, "equals", emptyList(), listOf(parameter),
            methodScope, BooleanType, emptyList(), body, DataClassGenerator.keywords, origin
        )
    }

}