package me.anno.zauber.logging

object LoggerUtils {
    fun disableCompileLoggers() {
        LogManager.disableLoggers(
            "TypeResolution,ASTSimplifier,MemberResolver,Inheritance,FindMemberMatch," +
                    "CallExpression,SuperCallExpression,CallWithNames,FieldMethodResolver," +
                    "MethodResolver,ResolvedMethod," +
                    "ConstructorResolver," +
                    "ResolvedField,Field,FieldResolver,FieldExpression," +
                    "SimpleGetField,SimpleSetField"
        )
    }
}