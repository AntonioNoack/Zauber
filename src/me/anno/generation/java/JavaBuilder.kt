package me.anno.generation.java

import me.anno.generation.Specializations.foundTypeSpecialization
import me.anno.generation.Specializations.specialization
import me.anno.generation.java.JavaSourceGenerator.comment
import me.anno.generation.java.JavaSourceGenerator.createClassName
import me.anno.generation.java.JavaSourceGenerator.createPackageName
import me.anno.generation.java.JavaSourceGenerator.protectedTypes
import me.anno.zauber.ast.rich.TypeOfField
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.typeresolution.ParameterList.Companion.resolveGenerics
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.*
import me.anno.zauber.types.specialization.Specialization

object JavaBuilder {

    val builder = JavaSourceGenerator.builder

    fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val protected = protectedTypes[type]
        if (protected != null) {
            builder.append(if (needsBoxedType) protected.boxed else protected.native)
            return
        }

        var type = type
        while (true) {
            try {
                val resolved = type.resolve().specialize()
                if (resolved == type) break
                type = resolved
            } catch (e: Exception) {
                e.printStackTrace()
                break
            }
        }

        when (type) {
            NullType -> {
                builder.append("Object ")
                comment { builder.append("null") }
            }
            Types.Nothing -> {
                builder.append("Object ")
                comment { builder.append("Nothing") }
            }
            is ClassType -> appendClassType(type, scope, needsBoxedType)
            is UnionType if type.types.size == 2 && NullType in type.types -> {
                // builder.append("@org.jetbrains.annotations.Nullable ")
                appendType(
                    type.types.first { it != NullType }, scope,
                    true /* native types cannot be null */
                )
                comment { builder.append("or null") }
            }
            is SelfType if (type.scope == scope) -> builder.append(scope.name)
            is ThisType -> appendType(type.type, scope, needsBoxedType)
            UnknownType -> builder.append('?')
            is LambdaType -> {
                val selfType = type.selfType
                builder.append("zauber.Function")
                    .append(type.parameters.size + if (selfType != null) 1 else 0)
                    .append('<')
                if (selfType != null) {
                    appendType(selfType, scope, true)
                    builder.append(", ")
                }
                for (param in type.parameters) {
                    appendType(param.type, scope, true)
                    builder.append(", ")
                }
                appendType(type.returnType, scope, true)
                builder.append('>')
            }
            is GenericType -> {
                val lookup = specialization[type]
                if (lookup != null) appendType(lookup, scope, needsBoxedType)
                else {
                    comment { builder.append(type.scope.pathStr) }
                    builder.append(type.name)
                }
            }
            is TypeOfField -> {
                val valueType = type.resolve()
                appendType(valueType, scope, needsBoxedType)
            }
            else -> {
                builder.append("Object ")
                comment {
                    builder.append(type)
                        .append(" (")
                        .append(type.javaClass.simpleName)
                        .append(')')
                }
            }
        }
    }

    fun appendClassType(type: ClassType, scope: Scope, needsBoxedType: Boolean) {

        if (type.clazz.scopeType == ScopeType.TYPE_ALIAS) {
            val newType0 = type.clazz.selfAsTypeAlias!!
            val newType = type.typeParameters.resolveGenerics(
                null, /* used for 'This'/'Self' */ newType0
            )
            appendType(newType, scope, needsBoxedType)
            return
        }

        val params = type.typeParameters
        if (!params.isNullOrEmpty()) {
            val spec = Specialization(type)
            val className = createClassName(type.clazz, spec)
            builder.append(type.clazz.parent!!.pathStr).append('.')
                .append(className)
            foundTypeSpecialization(type.clazz, spec)
        } else {
            builder.append(type.clazz.pathStr)
        }

        if (type.clazz.scopeType == ScopeType.PACKAGE) {
            val extraName = createPackageName(type.clazz, Specialization(type))
            builder.append('.').append(extraName)
        }
    }
}