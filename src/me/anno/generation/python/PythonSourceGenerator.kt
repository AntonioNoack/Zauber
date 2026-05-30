package me.anno.generation.python

import me.anno.generation.BoxedType
import me.anno.generation.FileEntry
import me.anno.generation.FileWithImportsWriter
import me.anno.generation.Specializations.specialization
import me.anno.generation.c.CSourceGenerator.Companion.hashMethodParameters
import me.anno.generation.java.JavaSourceGenerator
import me.anno.generation.java.JavaSuperCallWriter.appendSuperCallParams
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.reverse.SimpleTailCall
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.expression.constants.SpecialValueExpression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.rich.parameter.InnerSuperCall
import me.anno.zauber.ast.rich.parameter.InnerSuperCallTarget
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parameter.SuperCall
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.expression.SimpleAllocateInstance
import me.anno.zauber.ast.simple.expression.SimpleAssignment
import me.anno.zauber.ast.simple.expression.SimpleCall
import me.anno.zauber.ast.simple.expression.SimpleConstructorCall
import me.anno.zauber.ast.simple.fields.LocalField
import me.anno.zauber.ast.simple.fields.SimpleField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.scope.Scope
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.typeresolution.ValueParameterImpl
import me.anno.zauber.typeresolution.members.ConstructorResolver
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.GenericType
import java.io.File

// todo this is just like JavaScript source code,
//  just a little different indentation, and classes look different
class PythonSourceGenerator : JavaSourceGenerator() {

    companion object {
        val protectedPythonTypes by threadLocal {
            Types.run {
                mapOf(
                    Boolean to BoxedType("Boolean", "bool"),
                    Byte to BoxedType("Byte", "int"),
                    Short to BoxedType("Short", "int"),
                    Int to BoxedType("Int", "int"),
                    Long to BoxedType("Long", "int"),
                    Char to BoxedType("Char", "int"), // string, but math is difficult...
                    Float to BoxedType("Float", "float"),
                    Double to BoxedType("Double", "float"),
                )
            }
        }

        val nativePythonTypes by threadLocal { protectedPythonTypes.filter { (_, it) -> it.boxed != it.native } }
        val nativePythonNumbers by threadLocal { nativePythonTypes - Types.Boolean }

        private const val STATIC_INSTANCE_SUFFIX = "_S"
    }

    override val protectedTypes: Map<ClassType, BoxedType> get() = protectedPythonTypes
    override val nativeTypes: Map<ClassType, BoxedType> get() = nativePythonTypes
    override val nativeNumbers: Map<ClassType, BoxedType> get() = nativePythonNumbers

    override fun getExtension(headerOnly: Boolean): String = "py"

    override fun getMethodName(method0: Specialization): String {

        if (method0.method is Constructor && method0.method.ownerScope.typeWithArgs in nativeNumbers) {
            return "__init__"
        }

        val base = if (method0.method is Constructor) "init_" else super.getMethodName(method0)
        return "${base}_${hashMethodParameters(method0)}"
    }

    override fun getMainMethodFile(dst: File): File {
        return File(dst, "main.${getExtension(false)}")
    }

    override fun defineNullableAnnotation(dst: File, writer: FileWithImportsWriter) {
        // skip
    }

    override fun defineMainMethodCallEntry(
        dst: File, writer: FileWithImportsWriter,
        mainMethod: Method, className: String
    ): FileEntry {
        val needsArgs = mainMethod.valueParameters.isNotEmpty()
        val spec = Specialization(mainMethod.memberScope, emptyParameterList())
        val methodName = getMethodName(spec)
        return FileEntry(emptyList(), this)
            .apply {
                content.append(
                    """
                $className.$methodName(${if (needsArgs) "args" else ""});
            """.trimIndent()
                )
            }
    }

    override fun appendPackageDeclaration(packagePath: List<String>, file: File) {
        builder.append("# ")
        appendPath(packagePath)
        builder.append('.')
        builder.append(file.nameWithoutExtension)
        nextLine()
        nextLine()
    }

    override fun appendImport(packagePath: List<String>, import: List<String>, importedScope: Scope?) {
        builder.append("from ")
        appendPath(import)
        builder.append(" import ")
        builder.append(import.last())

        if (importedScope != null && importedScope.isObjectLike()) {
            builder.append(STATIC_INSTANCE_SUFFIX)
        }

        nextLine()
    }

    override fun appendArrayContentField(classScope: Scope, headerOnly: Boolean) {
        builder.append("content = null;")
        nextLine()
    }

    override fun getClassType(scope: Scope): String {
        // todo what about enums?
        return "class"
    }

    override fun appendClassFlags(scope: Scope) {
        if (scope.flags.hasFlag(Flags.ABSTRACT)) builder.append("abstract ")
    }

    override fun appendClass(
        className: String, classScope: Scope, specialization: Specialization,
        methods: Collection<Specialization>, fields: Collection<Specialization>,
        headerOnly: Boolean
    ) {
        declareImport(classScope, specialization)
        specialization.use {
            appendSpecializationInfoComment()

            appendClassFlags(classScope)
            appendClassPrefix(classScope, className)

            // skipped type parameters
            appendSuperTypes(classScope)

            appendClassBody(classScope, className, methods, fields, headerOnly)
        }
    }

    override fun appendSuperTypes(scope: Scope) {
        if (scope.superCalls.isEmpty() && scope != Types.Any.clazz) {
            scope.superCalls.add(SuperCall(Types.Any, emptyList(), null, -1))
        }
        builder.append('(')
        for (superCall in scope.superCalls) {
            if (superCall.isInterfaceCall) continue
            val type = superCall.type
            if (!builder.endsWith('(')) builder.append(", ")
            appendType(type, scope, true)
        }
        builder.append(')')
    }

    override fun appendFieldFlags(classScope: Scope, field: Field, allowFinal: Boolean) {
        if (field == classScope.objectField) builder.append("static ")
    }

    override fun appendConstructorFlags(classScope: Scope, constructor: Constructor, headerOnly: Boolean) {
        // nothing
        builder.append("def ")
    }

    override fun appendConstructorHeader(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        appendConstructorFlags(classScope, constructor, headerOnly)
        check(specialization.method === constructor)
        if (classScope.isObjectLike()) builder.append("__init__")
        else builder.append(getMethodName(specialization))
        appendValueParameterDeclaration(null, constructor.valueParameters, classScope)
    }

    override fun appendConstructor(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        // some spacing
        nextLine()

        appendConstructorHeader(classScope, className, constructor, headerOnly)
        appendConstructorBody(classScope, className, constructor, headerOnly)
    }

    override fun appendConstructorBody(
        classScope: Scope, className: String,
        constructor: Constructor, headerOnly: Boolean
    ) {
        val body = constructor.body
        val context = ResolutionContext(constructor.selfType, true, null, emptyMap())

        writeBlock {

            val i0 = builder.length

            if (false) {
                // todo do this later, somehow...
                appendSuperCall0(classScope, className, constructor)
                nextLine()
            }

            if (classScope == Types.Array.clazz) {
                appendArrayContentInitialization(constructor)
            }

            if (body != null) {
                val methodSpec = specialization
                check(methodSpec.method === constructor)
                appendCode(context, methodSpec, body, true)
            }

            removeTrailingWhitespace()
            nextLine()

            if (builder.length <= i0) {
                builder.append("pass")
                nextLine()
            }
        }
    }

    override fun appendCode(
        context: ResolutionContext, method1: Specialization,
        body: Expression, skipSuperCall: Boolean
    ) {
        val graph = ASTSimplifier.simplify(method1)
        if (skipSuperCall) graph.removeSuperCalls()
        prepareGraph(graph)

        // todo simplify all entry points as methods...

        val pos0 = builder.length
        declareLocalFields(graph)

        if (graph.hasTailCalls()) appendTailCallCode(graph)
        else appendSimpleBlock(graph, graph.startBlock)

        removeTailingReturn()

        appendMissingDeclarations(graph, pos0)
    }

    override fun appendMissingDeclarations(graph: SimpleGraph, pos0: Int) {
        val pos1 = builder.length
        for (field in usedFields - declaredFields) {
            appendDeclare(graph, field, graph.method.memberScope, false)
            builder.append(" = ")
            appendDefaultValue(field.type)
            nextLine()
        }
        swapSections(pos0, pos1)

        usedFields.clear()
        declaredFields.clear()
    }

    override fun appendSuperCall0(classScope: Scope, className: String, constructor: Constructor) {
        // interfaces don't need super calls :)
        val superCall = constructor.superCall
        val superType0 = classScope.superCalls
            .firstOrNull { it.isClassCall }?.typeI
            ?: Types.Any
        val superType = resolveType(superType0)

        if (superCall != null) {
            appendSuperCall0Name(
                classScope, className, constructor,
                superType as ClassType, superCall
            )

            if (!classScope.isObjectLike()) {
                // find out hash of super-call...
                val context = ResolutionContext(null, specialization, true, null)
                val valueParams = superCall.valueParameters.map {
                    val type = it.value.resolveReturnType(context)
                    ValueParameterImpl(it.name, type, false)
                }
                val foundConstructor = ConstructorResolver.findMemberInScope(
                    superType.clazz, superCall.origin, superType.clazz.name,
                    null, valueParams, context
                )
                    ?: throw IllegalStateException("Missing $superCall in $superType for $className, valueParams: $valueParams")
                builder.append(".__init__")
                    .append(hashMethodParameters(foundConstructor.specialization))
            }

            val context = ResolutionContext(null, specialization, true, null)
            appendSuperCallParams(context, superCall)
        } else {
            comment { builder.append("superCall is null") }
        }
    }

    override fun appendSuperCall0Name(
        classScope: Scope, className: String, constructor: Constructor,
        superType: Type, superCall: InnerSuperCall
    ) {
        if (superCall.target == InnerSuperCallTarget.THIS) {
            builder.append("this") // is this supported? yes
        } else {
            builder.append("super")
        }
    }

    override fun appendBackingField(classScope: Scope, field: Field, allowFinal: Boolean, headerOnly: Boolean) {
        appendFieldFlags(classScope, field, allowFinal)

        var valueType = (field.valueType ?: Types.NullableAny)
        valueType = valueType.resolve(classScope)
        valueType = resolveType(valueType)

        appendFieldName(field)
        builder.append(" = ")
        appendDefaultValue(valueType)
        nextLine()
    }

    override fun appendMethodFlags(classScope: Scope, method0: Specialization, headerOnly: Boolean) {
        builder.append("def ")
    }

    override fun appendMethodHeader(
        classScope: Scope, className: String,
        method0: Specialization, headerOnly: Boolean
    ) {
        appendMethodFlags(classScope, method0, headerOnly)

        builder.append(getMethodName(method0))

        val method = method0.method as Method
        assignSelfType(classScope, method)
        appendValueParameterDeclaration(method.selfTypeIfNecessary, method.valueParameters, classScope)
    }

    override fun appendMethod(classScope: Scope, className: String, method0: Specialization, headerOnly: Boolean) {
        // some spacing
        nextLine()

        appendMethodHeader(classScope, className, method0, headerOnly)
        writeBlock { appendMethodBody(method0, headerOnly) }
    }

    override fun appendMethodBody(methodSpec: Specialization, headerOnly: Boolean) {
        val nativeImpl = getNativeImplementation(methodSpec.method)
        val body = methodSpec.method.body

        val i0 = builder.length
        when {
            body != null -> {
                val context = ResolutionContext(methodSpec.method.selfType, methodSpec, true, null)
                appendCode(context, methodSpec, body, false)
            }
            nativeImpl != null -> {
                appendNativeImplementation(nativeImpl, methodSpec.method)
            }
            isArrayGetter(methodSpec) -> appendArrayGetter(methodSpec)
            isArraySetter(methodSpec) -> appendArraySetter(methodSpec)
            else -> {
                builder.append("raise \"Missing implementation for $methodSpec\"")
                nextLine()
            }
        }

        if (i0 == builder.length) {
            builder.append("pass")
            nextLine()
        }
    }

    override fun appendNativeImplementation(nativeImpl: String, method: MethodLike) {
        val i0 = builder.length
        builder.append(nativeImpl)
        nextLine()
        appendReturnIfMissing(method, i0)
    }

    override fun appendArrayContentInitialization(constructor: Constructor) {
        val elementType = specialization.typeParameters[0]
        val sizeName = constructor.valueParameters[0].newName
        builder.append("self.content = [")
        appendDefaultValue(elementType)
        builder.append("] * ").append(sizeName)
        nextLine()
    }

    override fun appendDefaultValue(valueType: Type) {
        val str = when (valueType) {
            Types.Boolean -> "False"
            in nativeTypes -> if (isNumberFloat(valueType)) "0.0" else "0"
            else -> "None"
        }
        builder.append(str)
    }

    override fun appendReturnIfMissing(method: MethodLike, i0: Int) {
        if (builder.indexOf("return", i0) < 0) {
            builder.append("return ")
            appendGetObjectInstance(Types.Unit.clazz, method.scope)
            nextLine()
        }
    }

    override fun appendArrayGetter(method0: Specialization) {
        builder.append("return self.content[index]")
        nextLine()
    }

    override fun appendArraySetter(method0: Specialization) {
        builder.append("self.content[index] = value")
        nextLine()

        builder.append("return ")
        appendGetObjectInstance(Types.Unit.clazz, method0.method.memberScope)
        nextLine()
    }

    override fun declareLocalField(graph: SimpleGraph, field: LocalField) {
        builder.append(field.name).append(" = None")
        nextLine()
    }

    override fun appendClassBody(
        classScope: Scope, className: String,
        methods: Collection<Specialization>,
        fields: Collection<Specialization>,
        headerOnly: Boolean
    ) {
        writeBlock {
            appendConstructors(classScope, className, methods, headerOnly)
            appendMethods(classScope, className, methods, headerOnly)
        }

        if (classScope.isObjectLike()) {
            nextLine()
            appendStaticInstance(classScope, className)
        }
    }

    override fun appendStaticInstance(classScope: Scope, className: String) {
        builder.append(className).append(STATIC_INSTANCE_SUFFIX)
            .append(" = ").append(className).append("()")
        nextLine()
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        appendType(objectScope.typeWithArgs, objectScope, false)
        builder.append(STATIC_INSTANCE_SUFFIX)
    }

    override fun appendDeclare(graph: SimpleGraph, dst: SimpleField, scope: Scope, withEquals: Boolean) {
        appendFieldName(graph, dst)
        if (withEquals) builder.append(" = ")
    }

    override fun appendValueParameterDeclaration(
        selfTypeIfNecessary: Type?,
        valueParameters: List<Parameter>, scope: Scope
    ) {
        builder.append("(self")
        if (selfTypeIfNecessary != null) {
            builder.append(", __self")
        }
        for (param in valueParameters) {
            builder.append(", ")
            appendFieldName(param)
        }
        builder.append(')')
    }

    override fun appendInstrSuffix(graph: SimpleGraph, expr: SimpleInstruction) {
        if (/*expr !is SimpleBlock &&*/ expr !is SimpleBranch) nextLine()
        if (expr is SimpleAssignment && expr.dst.type == Types.Nothing) {
            builder.append("raise AssertionError(\"Unreachable\")")
            nextLine()
        }
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleAllocateInstance -> {
                appendType(expr.allocatedType, expr.scope, true)
                builder.append("()")
            }
            is SimpleConstructorCall -> {
                appendFieldName(graph, expr.thisInstance, ".")
                builder.append(getMethodName(expr.specialization))
                appendValueParams(graph, expr.valueParameters)
            }
            is SimpleBranch -> {
                builder.append("if ")
                appendFieldName(graph, expr.condition)
                writeBlock {
                    appendSimpleBlock(graph, expr.ifTrue)
                }
                if (expr.ifFalse != null) {
                    builder.append("else")
                    writeBlock {
                        appendSimpleBlock(graph, expr.ifFalse)
                    }
                }
            }
            is SimpleLoop -> {
                builder.append("while True")
                writeBlock {
                    if (expr.condition != null) {
                        appendSimpleBlock(graph, expr.conditionBlock!!)
                        builder.append("if ")
                        if (!expr.negate) builder.append("not ")
                        appendFieldName(graph, expr.condition)
                        builder.append(":"); nextLine()
                        builder.append("  break"); nextLine()
                        nextLine()
                    }
                    appendSimpleBlock(graph, expr.body)
                }
            }
            is SimpleTailCall -> {
                builder.append("nextBlockId = ").append(expr.toBeCalled.blockId)
                nextLine()
                builder.append("raise StopIteration")
            }
            is SimpleCall -> {
                // Number.toX() needs to be converted to a cast
                val methodName = expr.methodName
                val done = when (expr.valueParameters.size) {
                    0 -> appendUnaryOperator(graph, expr, methodName)
                    1 -> {
                        val type = expr.thisInstance.type
                        val supportsType = when (type) {
                            Types.String, in nativeTypes -> true
                            else -> false
                        }
                        val symbol = when (methodName) {
                            "div" -> if (isNumberFloat(type)) " / " else " // "
                            else -> getBinarySymbol(methodName)
                        }
                        if (supportsType && symbol != null) {
                            appendFirstParameter(graph, type, expr)
                            builder.append(symbol)
                            appendFieldName(graph, expr.valueParameters[0])
                            true
                        } else false
                    }
                    else -> false
                }
                if (!done) {
                    appendCallImpl(graph, expr)
                }
            }
            else -> super.appendInstrImpl(graph, expr)
        }
    }

    override fun appendObjectInstance(field: Field, exprScope: Scope, forFieldAccess: String) {
        if (field.ownerScope == outsideClassLike(exprScope)) {
            // if there is nothing dangerous in-between, we could use this.
            builder.append("self")
        } else {
            appendGetObjectInstance(field.ownerScope, exprScope)
        }
        builder.append(forFieldAccess)
    }

    override fun appendType(type: Type, scope: Scope, needsBoxedType: Boolean) {
        val type = resolveType(type)

        if (!needsBoxedType) {
            val protected = protectedTypes[type]
            if (protected != null) {
                builder.append(protected.native)
                return
            }
        }

        if (type is GenericType) {
            return appendTypeImpl(type.superBounds, scope, needsBoxedType)
        }

        appendTypeImpl(type, scope, needsBoxedType)
    }

    override fun appendNativeCall(needsCastForFirstValue: BoxedType, expr: SimpleCall, graph: SimpleGraph) {
        // ensure import
        val selfType = expr.thisInstance.type
        val position = builder.length
        appendType(selfType, expr.scope, true)
        builder.setLength(position)

        builder.append(needsCastForFirstValue.boxed).append("(")
        appendFieldName(graph, expr.thisInstance)
        builder.append(").")
        builder.append(getMethodName(expr.specialization))
        appendValueParams(graph, expr.valueParameters)
    }

    override fun appendNumber(type: Type, expr: NumberExpression) {
        when (type) {
            Types.Int, Types.UInt,
            Types.Long, Types.ULong -> builder.append(expr.asInt)
            Types.Float, Types.Half -> builder.append(expr.asFloat.toFloat()) // f is not supported
            Types.Double -> builder.append(expr.asFloat)
            else -> throw NotImplementedError("Append number of type $type")
        }
    }

    override fun appendCopy(graph: SimpleGraph, valueType: Type) {
        builder.append(".copy_0()")
    }

    override fun filterImports(name: String, packageScope: Scope, headerOnly: Boolean) {
        // remove self-include
        imports.remove(name)
    }

    override fun appendTailCallCode(graph: SimpleGraph) {
        builder.append("nextBlockId = 0"); nextLine()
        builder.append("while True")
        writeBlock {
            builder.append("try")
            writeBlock {
                builder.append("match nextBlockId")
                writeBlock {
                    val targets = findTailCallTargets(graph)
                    val blocks = graph.blocks
                    for (i in blocks.indices) {
                        val block = blocks[i]
                        if (i == 0 || targets[block.blockId]) {
                            builder.append("case ").append(block.blockId)
                            writeBlock {
                                appendSimpleBlock(graph, block)
                            }
                        }
                    }
                }
            }
            builder.append("except StopIteration")
            writeBlock {
                builder.append("pass")
            }
        }
        nextLine()
    }

    override fun comment(body: () -> Unit) {
        commentDepth++
        try {
            builder.append("# ")
            val len0 = builder.length
            body()
            removeTrailingWhitespace()
            for (i in len0 until builder.length) {
                if (builder[i] == '\n') builder[i] = '#'
            }
            nextLine()
        } finally {
            commentDepth--
        }
    }

    override fun writeBlock(run: () -> Unit) {
        builder.append(':')

        indentation++
        nextLine()

        try {
            run()
            dedent()
        } finally {
            indentation--
        }
    }

    override fun appendFieldName(graph: SimpleGraph, field: SimpleField, forFieldAccess: String) {
        if (field.isOwnerThis(graph)) {
            builder.append("self")
        } else if (field.isObjectLike()) {
            val objectScope = (field.type as ClassType).clazz
            appendGetObjectInstance(objectScope, graph.method.scope)
        } else {
            val field = field.dst
            when (val expr = field.constantRef) {
                is NumberExpression -> appendNumber(field.type, expr)
                is SpecialValueExpression -> when (expr.type) {
                    SpecialValue.NULL -> builder.append("None")
                    SpecialValue.TRUE -> builder.append("True")
                    SpecialValue.FALSE -> builder.append("False")
                }
                null -> {
                    check(field.id >= 0) { "Invalid field $field in $graph" }
                    val localField = field.fromLocalField
                    if (localField != null) {
                        builder.append(localField.name)
                    } else {
                        builder.append("tmp").append(field.id)
                        usedFields.add(field)
                    }
                }
                else -> throw NotImplementedError("Append constant field $expr")
            }
        }
        builder.append(forFieldAccess)
    }

}