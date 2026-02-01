package me.anno.zauber.ast.rich

import me.anno.langserver.VSCodeType
import me.anno.zauber.ast.rich.expression.ExpressionList
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.expression.unresolved.AssignmentExpression
import me.anno.zauber.ast.rich.expression.unresolved.ConstructorExpression
import me.anno.zauber.ast.rich.expression.unresolved.FieldExpression
import me.anno.zauber.tokenizer.TokenType
import me.anno.zauber.typeresolution.CallWithNames.createArrayOfExpr
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Types.ListType
import me.anno.zauber.types.impl.ClassType

object EnumProperties {

    fun ZauberASTBuilderBase.readEnumBody(): Int {

        val origin0 = origin(i)
        var endIndex = tokens.findToken(i, ";")
        if (endIndex < 0) endIndex = tokens.size
        val enumScope = currPackage
        val companionScope = enumScope.getOrPut("Companion", ScopeType.COMPANION_OBJECT)
        // val needsPrimaryConstructor = companionScope.primaryConstructorScope == null
        val primaryConstructorScope = companionScope.getOrCreatePrimConstructorScope()
        primaryConstructorScope.selfAsConstructor = Constructor(
            emptyList(), primaryConstructorScope,
            null, ExpressionList(ArrayList(), primaryConstructorScope, origin0),
            Keywords.NONE, origin0
        )

        push(endIndex) {
            var ordinal = 0
            while (i < tokens.size) {
                // read enum value
                readAnnotations()

                val origin = origin(i)
                val name = consumeName(VSCodeType.ENUM_MEMBER, 0)

                val typeParameters = readTypeParameters(null)
                val valueParameters = if (tokens.equals(i, TokenType.OPEN_CALL)) {
                    readValueParameters()
                } else emptyList()

                val keywords = packKeywords()
                val entryScope = readClassBody(name, Keywords.NONE, ScopeType.ENUM_ENTRY_CLASS)
                // todo add name and id as parameters
                val extraValueParameters = listOf(
                    NamedParameter(null, NumberExpression((ordinal++).toString(), companionScope, origin)),
                    NamedParameter(null, StringExpression(name, companionScope, origin)),
                )
                val initialValue = ConstructorExpression(
                    enumScope, typeParameters,
                    extraValueParameters + valueParameters,
                    null, enumScope, origin
                )
                val valueType =
                    if (enumScope.typeParameters.isNotEmpty()) null // we need to resolve them
                    else enumScope.typeWithArgs
                val field = companionScope.addField(
                    companionScope.typeWithoutArgs,
                    false, isMutable = false, null,
                    name, valueType, initialValue, keywords, origin
                )
                entryScope.objectField = field

                val fieldExpr = FieldExpression(field, primaryConstructorScope, origin)
                primaryConstructorScope.code.add(AssignmentExpression(fieldExpr, initialValue))

                readComma()
            }
        }

        createEnumProperties(companionScope, enumScope, origin0)
        return endIndex
    }

    fun createEnumProperties(companionScope: Scope, enumScope: Scope, origin: Int) {

        companionScope.hasTypeParameters = true

        val constructorScope = companionScope.getOrCreatePrimConstructorScope()
        val listType = ClassType(ListType.clazz, listOf(enumScope.typeWithoutArgs), origin)
        val entryValues = enumScope.enumEntries.map { entryScope ->
            val field = entryScope.objectField!!
            FieldExpression(field, constructorScope, origin)
        }
        val initialValue = createArrayOfExpr(enumScope.typeWithoutArgs, entryValues, constructorScope, origin)

        val entriesField = constructorScope.addField(
            companionScope.typeWithoutArgs,
            false, isMutable = false, null,
            "entries", listType,
            initialValue, Keywords.SYNTHETIC, origin
        )

        val entriesFieldExpr = FieldExpression(entriesField, constructorScope, origin)
        constructorScope.code.add(AssignmentExpression(entriesFieldExpr, initialValue))
    }

}