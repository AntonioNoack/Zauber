package me.anno.generation

import me.anno.zauber.logging.LogManager

object LoggerUtils {
    fun disableCompileLoggers() {
        LogManager.disableLoggers(
            "TypeResolution,ASTSimplifier,MemberResolver,Inheritance," +
                    "CallExpression,SuperCallExpression,CallWithNames,FieldMethodResolver," +
                    "MethodResolver,ResolvedMethod," +
                    "ConstructorResolver," +
                    "ResolvedField,Field,FieldResolver,FieldExpression"
        )
    }
}