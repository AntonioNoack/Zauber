package me.anno.zauber.generation.java

import me.anno.zauber.ast.KeywordSet
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Keywords.hasFlag
import me.anno.zauber.ast.rich.controlflow.IfElseBranch
import me.anno.zauber.ast.rich.expression.*
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.ASTSimplifier.needsFieldByParameter
import me.anno.zauber.generation.DeltaWriter
import me.anno.zauber.generation.Generator
import me.anno.zauber.generation.java.JavaExpressionWriter.appendSuperCall
import me.anno.zauber.generation.java.JavaSimplifiedASTWriter.appendSimplifiedAST
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.members.ResolvedMember.Companion.resolveGenerics
import me.anno.zauber.types.Scope
import me.anno.zauber.types.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types.AnyType
import me.anno.zauber.types.Types.BooleanType
import me.anno.zauber.types.Types.ByteType
import me.anno.zauber.types.Types.CharType
import me.anno.zauber.types.Types.DoubleType
import me.anno.zauber.types.Types.FloatType
import me.anno.zauber.types.Types.IntType
import me.anno.zauber.types.Types.LongType
import me.anno.zauber.types.Types.NothingType
import me.anno.zauber.types.Types.NullableAnyType
import me.anno.zauber.types.Types.ShortType
import me.anno.zauber.types.Types.StringType
import me.anno.zauber.types.impl.*
import java.io.File

// todo before generating JVM bytecode, create source code to be compiled with a normal Java compiler
//  big difference: stack-based
object JavaSourceGenerator : Generator() {

    val protectedTypes = mapOf(
        StringType to BoxedType("java.lang.String", "java.lang.String"),
        BooleanType to BoxedType("Boolean", "boolean"),
        ByteType to BoxedType("Byte", "byte"),
        ShortType to BoxedType("Short", "short"),
        IntType to BoxedType("Integer", "int"),
        LongType to BoxedType("Long", "long"),
        CharType to BoxedType("Character", "char"),
        FloatType to BoxedType("Float", "float"),
        DoubleType to BoxedType("Double", "double"),
        AnyType to BoxedType("Object", "Object")
    )

    val nativeTypes = protectedTypes.filter { (_, it) -> it.boxed != it.native }

    private val blacklistedPaths = listOf(listOf("java"))

    override fun generateCode(dst: File, root: Scope) {
        val writer = DeltaWriter(dst)
        try {
            writer.write(
                File(dst, "org/jetbrains/annotations/Nullable.java"),
                "package org.jetbrains.annotations;\n\n" +
                        "import java.lang.annotation.*;\n\n" +
                        "@Retention(RetentionPolicy.RUNTIME)\n" +
                        "@Target(ElementType.TYPE)\n" +
                        "public @interface Nullable {}\n"
            )
            generate(root, dst, writer)
        } finally {
            writer.finish()
        }
    }

    // todo add line to imports where needed
    // todo if a type is defined twice, we still need the full path
    // todo if the parent is the same as scope.parent, we can skip the import

    fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val protected = protectedTypes[type]
        if (protected != null) {
            builder.append(if (needsBoxedType) protected.boxed else protected.native)
            return
        }

        when (type) {
            NullType -> builder.append("Object /* null */")
            NothingType -> builder.append("Object /* Nothing */")
            is ClassType -> appendClassType(type, scope, needsBoxedType)
            is UnionType if type.types.size == 2 && NullType in type.types -> {
                // builder.append("@org.jetbrains.annotations.Nullable ")
                appendType(type.types.first { it != NullType }, scope, needsBoxedType)
                builder.append("/* or null */")
            }
            is SelfType if (type.scope == scope) -> builder.append(scope.name)
            is ThisType -> appendType(type.type, scope, needsBoxedType)
            is GenericType if (type.scope == scope) -> builder.append(type.name)
            UnknownType -> builder.append('?')
            is LambdaType -> {
                // todo define all these classes...
                // todo add respective import...
                builder.append("zauber.Function").append(type.parameters.size)
                    .append('<')
                for (param in type.parameters) {
                    appendType(param.type, scope, true)
                    builder.append(", ")
                }
                appendType(type.returnType, scope, true)
                builder.append('>')
            }
            is GenericType -> builder.append(type.name)
            else -> {
                builder.append("Object /* $type */")
            }
        }
    }

    fun appendClassType(type: ClassType, scope: Scope, isGeneric: Boolean) {

        if (type.clazz.scopeType == ScopeType.TYPE_ALIAS) {
            val newType0 = type.clazz.selfAsTypeAlias!!
            val genericNames = type.clazz.typeParameters
            val genericValues = type.typeParameters
            val newType = resolveGenerics(
                null, /* used for 'This'/'Self' */ newType0, genericNames,
                if (genericValues != null) ParameterList(genericNames, genericValues)
                else ParameterList(genericNames),
            )
            appendType(newType, scope, isGeneric)
            return
        }

        builder.append(type.clazz.pathStr)

        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            builder.append('<')
            for (i in params.indices) {
                if (i > 0) builder.append(", ")
                val param = params.getOrNull(i) ?: NullableAnyType
                appendType(param, scope, true)
            }
            builder.append('>')
        }
    }

    private fun appendPackage(path: List<String>) {
        builder.append("package ")
            .append(path.joinToString("."))
            .append(";\n\n")
    }

    fun generate(scope: Scope, dst: File, writer: DeltaWriter) {
        val scopeType = scope.scopeType
        if (scopeType != null && scopeType.isClassType()) {
            val file = File(dst, scope.path.joinToString("/") + ".java")

            appendPackage(scope.path.dropLast(1))
            generateInside(scope.name, scope)

            writer.write(file, finish())
        } else if ((scopeType == ScopeType.PACKAGE || scopeType == null) && scope.path !in blacklistedPaths) {
            if (scopeType == ScopeType.PACKAGE &&
                (scope.fields.isNotEmpty() || scope.methods.isNotEmpty())
            ) {
                val name = scope.name.capitalize() + "Kt"
                val file = File(dst, scope.path.joinToString("/") + "/$name.java")

                appendPackage(scope.path)
                generateInside(name, scope)

                writer.write(file, finish())
            }

            for (child in scope.children) {
                generate(child, dst, writer)
            }
        }
    }

    fun writeBlock(run: () -> Unit) {
        builder.append(" {")

        depth++
        nextLine()

        run()

        if (builder.endsWith("  ")) {
            builder.setLength(builder.length - 2)
        }
        depth--
        builder.append("}\n")
        indent()
    }

    fun generateInside(name: String, scope: Scope) {

        // todo imports ->
        //  collect them in a dry-run :)


        builder.append("public ")
        val type = when (scope.scopeType) {
            ScopeType.ENUM_CLASS -> "final class"
            ScopeType.NORMAL_CLASS -> {
                val isFinal = isFinal(scope.keywords)
                if (isFinal) "final class" else "class"
            }
            ScopeType.INTERFACE -> "interface"
            ScopeType.OBJECT, ScopeType.COMPANION_OBJECT -> "final class"
            ScopeType.ENUM_ENTRY_CLASS -> "final class"
            ScopeType.PACKAGE -> "final class"
            else -> scope.scopeType.toString()
        }
        if (scope.keywords.hasFlag(Keywords.ABSTRACT)) builder.append("abstract ")
        builder.append(type).append(' ')
        builder.append(name)

        appendTypeParams(scope)
        appendSuperTypes(scope)

        writeBlock {

            appendFields(scope)
            appendConstructors(scope)
            appendMethods(scope)

            // inner classes
            if (scope.scopeType != ScopeType.PACKAGE) {
                for (child in scope.children) {
                    val childType = child.scopeType ?: continue
                    if (childType.isClassType()) {
                        // some spacing
                        nextLine()
                        generateInside(child.name, child)
                    }
                }
            }
        }
    }

    private fun appendFields(classScope: Scope) {
        if (classScope.scopeType == ScopeType.INTERFACE) return // no backing fields
        val fields = classScope.fields
        for (field in fields) {
            if (field.selfType != classScope.typeWithArgs) continue
            if (!needsFieldByParameter(field.byParameter)) continue
            if (!needsBackingField(field)) continue
            appendBackingField(classScope, field)
        }
    }

    private fun needsBackingField(field: Field): Boolean {
        val getter = field.getter
        val setter = field.setter
        if (getter?.body == null || getter.body.needsBackingField(getter.scope)) return true
        if (setter?.body?.needsBackingField(setter.scope) == true) return true
        return false
    }

    private fun appendBackingField(classScope: Scope, field: Field) {
        builder.append("public ")
        if (field.name == "__instance__") builder.append("static ")
        if (!field.isMutable) builder.append("final ")
        appendType(field.valueType ?: NullableAnyType, classScope, false)
        builder.append(' ').append(field.name).append(';')
        nextLine()
    }

    private fun appendMethods(classScope: Scope) {
        for (method in classScope.methods) {
            appendMethod(classScope, method)
        }
    }

    private fun appendConstructors(classScope: Scope) {
        for (constructor in classScope.constructors) {
            appendConstructor(classScope, constructor)
        }
    }

    private fun isFinal(keywords: KeywordSet): Boolean {
        return keywords.hasFlag(Keywords.FINAL) || (
                !keywords.hasFlag(Keywords.OPEN) &&
                        !keywords.hasFlag(Keywords.OVERRIDE)
                )
    }

    private fun appendMethod(classScope: Scope, method: Method) {

        // some spacing
        nextLine()

        val selfType = method.selfType
        val isBySelf = selfType == classScope.typeWithArgs ||
                method.keywords.hasFlag(Keywords.OVERRIDE) ||
                method.keywords.hasFlag(Keywords.ABSTRACT)

        if (method.keywords.hasFlag(Keywords.OVERRIDE)) {
            builder.append("@Override")
            nextLine()
        }

        if (method.keywords.hasFlag(Keywords.ABSTRACT) && classScope.scopeType != ScopeType.INTERFACE) {
            builder.append("abstract ")
        }

        if (classScope.scopeType != ScopeType.INTERFACE) builder.append("public ")
        if (method.keywords.hasFlag(Keywords.EXTERNAL)) builder.append("native ")
        if (classScope.scopeType != ScopeType.INTERFACE && isFinal(method.keywords)) builder.append("final ")
        if (classScope.scopeType == ScopeType.INTERFACE && method.body != null) builder.append("default ")
        // if (!isBySelf || classScope.scopeType?.isObject() == true) builder.append("static ")

        appendTypeParameterDeclaration(method.typeParameters, classScope)
        appendType(method.returnType ?: NullableAnyType, classScope, false)
        builder.append(' ').append(method.name)
        val selfTypeIfNecessary = if (!isBySelf) selfType else null
        method.selfTypeIfNecessary = selfTypeIfNecessary
        appendValueParameterDeclaration(selfTypeIfNecessary, method.valueParameters, classScope)
        val body = method.body
        if (body != null) {
            val context = ResolutionContext(classScope, method.selfType, true, null)
            appendCode(context, method, body)
        } else {
            builder.append(";")
            nextLine()
        }
    }

    private fun appendConstructor(classScope: Scope, constructor: Constructor) {

        // some spacing
        nextLine()

        builder.append("public ").append(classScope.name)
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
        // todo append extra body-block for super-call
        val body = constructor.body

        val isPrimaryConstructor = constructor == classScope.primaryConstructorScope?.selfAsConstructor

        val context = ResolutionContext(classScope, constructor.selfType, true, null)
        val superCall = constructor.superCall

        writeBlock {
            // todo I think this must be in one line... needs different writing, and cannot handle errors the traditional way...
            if (superCall != null) {
                appendSuperCall(context, superCall)
            } else {
                builder.append("// no super call")
                nextLine()
            }
            if (body != null) {
                appendCode(context, constructor, body)
            }
            if (isPrimaryConstructor) {
                for (body in constructor.scope.code) {
                    appendCode(context, constructor, body)
                }
            }
        }
    }

    private fun appendTypeParameterDeclaration(
        valueParameters: List<Parameter>,
        scope: Scope
    ) {
        if (valueParameters.isEmpty()) return
        builder.append('<')
        for (param in valueParameters) {
            if (!builder.endsWith("<")) builder.append(", ")
            builder.append(param.name)
            if (param.type != AnyType && param.type != NullableAnyType) {
                builder.append(" extends ")
                appendType(param.type, scope, false)
            }
        }
        builder.append("> ")
    }

    private fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>,
        scope: Scope
    ) {
        builder.append('(')
        if (selfTypeIfNecessary != null) {
            appendType(selfTypeIfNecessary, scope, false)
            builder.append(" __self")
        }
        for (param in valueParameters) {
            if (!builder.endsWith("(")) builder.append(", ")
            appendType(param.type, scope, false)
            builder.append(' ').append(param.name)
        }
        builder.append(')')
    }

    private fun appendCode(context: ResolutionContext, method: MethodLike, body: Expression) {
        writeBlock {
            appendCodeWithoutBlock(context, method, body)
        }
    }

    private fun appendCodeWithoutBlock(context: ResolutionContext, method: MethodLike, body: Expression) {
        try {
            val simplified = ASTSimplifier.simplify(context, body)
            appendSimplifiedAST(method, simplified.startBlock)
        } catch (e: Throwable) {
            e.printStackTrace()
            builder.append(
                "/* [${e.javaClass.simpleName}: ${
                    e.message.toString()
                        .replace("/*", "[")
                        .replace("*/", "]")
                }] ${
                    body.toString()
                        .replace("/*", "[")
                        .replace("*/", "]")
                } */"
            )
            nextLine()
        }
    }

    private fun appendTypeParams(scope: Scope) {
        val typeParams = scope.typeParameters
        if (typeParams.isNotEmpty()) {
            builder.append('<')
            for ((i, param) in typeParams.withIndex()) {
                if (i > 0) builder.append(", ")
                builder.append(param.name)
                if (param.type != NullableAnyType) {
                    builder.append(" extends ")
                    appendType(param.type, scope, true)
                }
            }
            builder.append('>')
        }
    }

    private fun appendSuperTypes(scope: Scope) {
        val superCall0 = scope.superCalls.firstOrNull { it.valueParameters != null }
        if (superCall0 != null && superCall0.type != AnyType) {
            builder.append(" extends ")
            appendClassType(superCall0.type, scope, true)
        }
        var implementsKeyword = if (scope.scopeType == ScopeType.INTERFACE) " extends " else " implements "
        for (superCall in scope.superCalls) {
            if (superCall.valueParameters != null) continue
            builder.append(implementsKeyword)
            appendClassType(superCall.type, scope, true)
            implementsKeyword = ", "
        }
    }
}