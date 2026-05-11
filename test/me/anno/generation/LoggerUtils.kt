package me.anno.generation

import me.anno.zauber.logging.LogManager

object LoggerUtils {
    fun disableCompileLoggers() {
        LogManager.disableLoggers(
            "TypeResolution,ASTSimplifier,MemberResolver,Inheritance," +
                    "CallExpression,SuperCallExpression," +
                    "MethodResolver,ResolvedMethod," +
                    "ConstructorResolver," +
                    "ResolvedField,Field,FieldResolver,FieldExpression"
        )
    }
}