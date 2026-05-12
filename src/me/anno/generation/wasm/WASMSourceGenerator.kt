package me.anno.generation.wasm

import me.anno.generation.c.CSourceGenerator
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.rich.Constructor
import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.rich.Method
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.SimpleNode.Companion.isValue
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ResolutionContext
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.ClassSpecialization
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization
import java.io.File

/**
 * todo current issue: implement gcNew
 *
 * todo directly encode binary WASM
 * like with generating JVM bytecode, we should convert simple-fields back to a stack, where possible -> not really necessary
 * */
class WASMSourceGenerator(val binary: Boolean) : CSourceGenerator() {

    init {
        check(!binary) {
            "Binary code gen not yet implemented"
        }
    }

    val pointerType = WASMType.I32
    val functionTypes = HashMap<FunctionType, Int>()
    val functionTypeList = ArrayList<FunctionType>()

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        for (method in data.calledMethods) {
            getFunctionType(method)
        }
        beginModule()
        writeMethodTypes()

        for (method in data.calledMethods) {
            if (method.method.body != null) continue
            appendMethodImport(method)
        }

        for (method in data.calledMethods) {
            if (method.method.body == null) continue
            appendMethodCode(method)
        }

        endModule()

        dst.writeText(builder.toString())
        builder.clear()
    }

    override fun comment(body: () -> Unit) {
        commentDepth++
        try {
            builder.append(if (commentDepth == 1) ";; " else "(")
            body()
            if (commentDepth == 1) nextLine()
            else builder.append(")")
        } finally {
            commentDepth--
        }
    }

    override fun writeBlock(run: () -> Unit) {
        if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
        builder.append("(")

        depth++
        nextLine()

        try {
            run()

            if (builder.endsWith("  ")) {
                builder.setLength(builder.length - 2)
            }

            depth--
            builder.append(")\n")
            indent()
            depth++

        } finally {
            depth--
        }
    }

    fun beginModule() {
        builder.append("(module")
        depth++
        nextLine()

        val numPages = 64
        builder.append("(import \"js\" \"mem\" (memory ")
            .append(numPages).append("))")
        nextLine()
    }

    fun endModule() {
        builder.setLength(builder.length - 2)
        builder.append(")")
        depth--
    }

    fun writeMethodTypes() {
        for (i in functionTypeList.indices) {
            val type = functionTypeList[i]
            builder.append("(type \$t").append(i)
                .append(" (func (param")
            for (param in type.params) {
                builder.append(' ').append(param.wasmName)
            }
            builder.append(") (result")
            for (result in type.results) {
                builder.append(' ').append(result.wasmName)
            }
            builder.append(")))")
            nextLine()
        }
    }

    fun getWASMType(type: Type): WASMType {
        val type = resolveType(type)
        return when (type) {
            Types.Byte, Types.UByte, Types.Short, Types.UShort, Types.Int, Types.UInt -> WASMType.I32
            Types.Long, Types.ULong -> WASMType.I64

            Types.Float, Types.Half -> WASMType.F32
            Types.Double -> WASMType.F64

            else -> pointerType
        }
    }

    fun getFunctionType(method: MethodSpecialization): Int {
        method.specialization.use {
            val method = method.method
            method.scope[ScopeInitType.CODE_GENERATION]

            val params = ArrayList<WASMType>(
                1 + (if (method.explicitSelfType) 1 else 0) +
                        method.valueParameters.size
            )

            // self:
            params.add(pointerType)

            if (method.explicitSelfType) {
                val selfType = method.selfType!!
                params.add(getWASMType(selfType))
            }

            for (param in method.valueParameters) {
                params.add(getWASMType(param.type))
            }

            // todo throwing methods could return the value as an extra return value...
            //  or we finally use WASM exceptions :)

            val returnTypes = listOf(getWASMType(method.returnType!!))
            val functionType = FunctionType(params, returnTypes)
            return functionTypes.getOrPut(functionType) {
                functionTypeList.add(functionType)
                functionTypes.size
            }
        }
    }

    fun appendMethodHeader(typeIndex: Int, methodName: String, method: MethodLike, export: Boolean) {
        val type = functionTypeList[typeIndex]
        builder.append("(func $").append(methodName)
        if (export) builder.append(" (export \"").append(methodName).append("\")")
        builder.append(" (type \$t").append(typeIndex).append(")")
        appendParamWithName(pointerType, "this")
        var j = 1
        if (method.explicitSelfType) {
            appendParamWithName(type.params[1], "__self")
            j++
        }
        for (i in method.valueParameters.indices) {
            val param = method.valueParameters[i]
            appendParamWithName(type.params[j + i], param.name)
        }

        // return types
        val returnType = method.returnType!!
        builder.append(" (result ").append(getWASMType(returnType).wasmName)
            .append(")")

        depth++
        nextLine()
    }

    fun appendParamWithName(type: WASMType, name: String) {
        builder.append(" (param \$").append(name)
            .append(' ').append(type.wasmName).append(")")
    }

    override fun getMethodName(method: MethodSpecialization): String {
        val base = if (method.method is Constructor) "_init_" else super.getMethodName0(method)
        return "${method.method.ownerScope.pathStr}_${base}_${hashMethodParameters(method)}"
    }

    fun appendMethodCode(method: MethodSpecialization) {
        val type = getFunctionType(method)
        method.specialization.use {
            appendMethodHeader(type, getMethodName(method), method.method, true)

            val (method, spec) = method
            val body = method.body!!

            val context = ResolutionContext(method.selfType, spec, true, null)
            appendCode(context, method, body, false)

            if (method is Constructor) {
                // return is typically missing
                builder.append("i32.const 0")
                nextLine()
            }

            // close body
            builder.setLength(builder.length - 2)
            builder.append(")")
            depth--
            nextLine()
        }
    }

    fun appendMethodImport(method: MethodSpecialization) {
        // (import "env" "print_i32" (func $print_i32 (param i32)))
        val type = getFunctionType(method)
        method.specialization.use {
            val methodName = getMethodName(method)
            builder.append("(import \"env\" \"").append(methodName)
                .append("\" ")
            appendMethodHeader(type, methodName, method.method, false)
            trimWhitespaceAtEnd()
            builder.append("))")
            depth--
            nextLine()
        }
    }

    override fun appendCode(context: ResolutionContext, method: MethodLike, body: Expression, skipSuperCall: Boolean) {
        try {
            val method1 = MethodSpecialization(method, context.specialization)
            val graph = ASTSimplifier.simplify(method1)
            if (skipSuperCall) graph.removeSuperCalls()

            CodeReconstruction.createCodeFromGraph(graph)

            for ((self, dst) in graph.thisFields) {
                val (scope, explicit) = self
                if (explicit) {
                    TODO("Somehow get explicit self...")
                } else {
                    if (!scope.isObjectLike() && scope.isClassLike() && scope != method.scope && scope != method.ownerScope) {
                        TODO("Declare $self in $dst for $method")
                    } // else not needed
                }
            }

            declareLocalFieldsAsVariables(graph)

            if (method is Constructor && method.ownerScope.isObjectLike()) {
                val objectAddr = getObjectAddress(method.ownerScope)
                builder.append(pointerType.wasmName).append(".const ").append(objectAddr)
                builder.append(" i32.const 1 i32.store")
                nextLine()
            }

            appendSimplifiedAST(graph, graph.startBlock)
        } catch (e: Throwable) {
            e.printStackTrace()
            comment {
                builder.append(
                    "[${e.javaClass.simpleName}: ${e.message}] $body"
                        .replace("/*", "[")
                        .replace("*/", "]")
                )
            }
            nextLine()
        }
    }

    fun declareLocalFieldsAsVariables(graph: SimpleGraph) {
        for (field in graph.fields) {
            builder.append("(local \$tmp").append(field.id)
                .append(' ').append(getWASMType(field.type).wasmName).append(')')
            nextLine()
        }
    }

    override fun appendInstrPrefix(graph: SimpleGraph, expr: SimpleInstruction) {
        // nothing required
    }

    fun appendDrop() {
        builder.append("drop")
        nextLine()
    }

    fun appendUnreachable() {
        builder.append("unreachable")
        nextLine()
    }

    override fun appendAssign(graph: SimpleGraph, expression: SimpleAssignment) {
        builder.append("local.set \$tmp").append(expression.dst.id)
        nextLine()
    }

    override fun appendInstrSuffix(graph: SimpleGraph, expr: SimpleInstruction) {
        if (expr is SimpleAssignment && expr.dst.type != Types.Nothing && !expr.dst.isObjectLike()) {
            val notNeeded = expr.dst.numReads == 0
            if (notNeeded) {
                comment { appendAssign(graph, expr) }
                appendDrop()
            } else appendAssign(graph, expr)
        }
        if (expr is SimpleAssignment && expr.dst.type == Types.Nothing) {
            appendUnreachable()
        }
    }

    // todo start classIndex at 0
    var allocOffset = 64
    var classOverhead = 4
    val objectInstances = HashMap<Scope, Int>()

    val structs = HashMap<ClassSpecialization, WASMStruct>()

    fun getStruct(classSpecialization: ClassSpecialization): WASMStruct {
        return structs.getOrPut(classSpecialization) {
            createStruct(classSpecialization, structs.size)
        }
    }

    fun createStruct(classSpecialization: ClassSpecialization, classIndex: Int): WASMStruct {
        val (clazz, spec) = classSpecialization
        return spec.use {
            var offset = classOverhead
            val properties = clazz.fields
                .filter { isStoredField(it) }
                .map { field ->
                    val type = getWASMType(resolveType(field.valueType!!))
                    field to type
                }
                .sortedByDescending { it.second.byteSize }
                .map { (field, type) ->
                    offset = align(offset, type.byteSize)
                    val offset0 = offset
                    offset += type.byteSize
                    WASMProperty(field, type, offset0)
                }
            // align with biggest property
            val biggestMember = properties.maxOfOrNull { it.wasmType.byteSize } ?: 4
            offset = align(offset, biggestMember)
            WASMStruct(classIndex, offset, properties)
        }
    }

    fun align(pos: Int, n: Int): Int {
        val mod = pos % n
        return if (mod != 0) pos + n - mod else pos
    }

    fun getObjectAddress(objectScope: Scope): Int {
        return objectInstances.getOrPut(objectScope) {
            val struct = getStruct(ClassSpecialization(objectScope, Specialization.noSpecialization))
            allocOffset = align(allocOffset, 8)
            val fixedAddress = allocOffset
            allocOffset = fixedAddress + struct.byteSize
            fixedAddress
        }
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        val offset = getObjectAddress(objectScope)
        builder.append(pointerType.wasmName).append(".const ").append(offset)
            .append(" ;; ").append(objectScope.pathStr)
        nextLine()
        builder.append("i32.load i32.eqz ")
        val method = objectScope.getOrCreatePrimaryConstructorScope().selfAsConstructor!!
        val methodInitName = getMethodName(MethodSpecialization(method, Specialization.noSpecialization))
        builder.append("(if (then ").append(pointerType.wasmName).append(".const ").append(offset)
            .append(" call $").append(methodInitName).append(" drop))")
        nextLine()
        builder.append(pointerType.wasmName).append(".const ").append(offset)
        nextLine()
    }

    fun insideObjectConstructor(graph: SimpleGraph, objectScope: Scope): Boolean {
        return objectScope.getOrCreatePrimaryConstructorScope().selfAsConstructor == graph.method
    }

    fun appendGetField(graph: SimpleGraph, field: SimpleField) {
        if (field.isOwnerThis(graph)) {
            builder.append("local.get \$this")
            nextLine()
        } else if (field.isObjectLike()) {
            val objectScope = (field.type as ClassType).clazz
            if (insideObjectConstructor(graph, objectScope)) {
                builder.append("local.get \$this")
                nextLine()
            } else {
                appendGetObjectInstance(objectScope, graph.method.scope)
            }
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            builder.append("local.get \$tmp").append(field.id)
            nextLine()
        }
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleNumber -> {
                when (expr.dst.type) {
                    Types.Byte, Types.UByte, Types.Short, Types.UShort,
                    Types.Int, Types.UInt -> builder.append("i32.const ").append(expr.base.value)
                    Types.Long, Types.ULong -> builder.append("i64.const ").append(expr.base.value)
                    Types.Float, Types.Half -> builder.append("f32.const ").append(expr.base.value)
                    Types.Double -> builder.append("f64.const ").append(expr.base.value)
                    else -> throw NotImplementedError("Append $expr")
                }
                nextLine()
            }
            is SimpleDeclaration -> {
                builder.append("(local $").append(expr.name)
                    .append(' ').append(getWASMType(expr.type).wasmName)
                    .append(")")
                nextLine()
            }
            is SimpleReturn -> {
                appendGetField(graph, expr.field)
                builder.append("return")
                nextLine()
            }
            is SimpleSelfConstructor -> {
                appendCallImpl(graph, expr)
            }
            is SimpleAllocateInstance -> {
                // todo test nullable variables
                // this allocation is a ClassType, so it cannot be null ever
                if (!expr.allocatedType.isValue()) {
                    // call GC-aware alloc instead
                    val struct = getStruct(ClassSpecialization(expr.allocatedType))
                    builder.append("i32.const ").append(struct.classIndex)
                        .append(" i32.const ").append(struct.byteSize)
                        .append(" call \$gcNew ;; ").append(expr.allocatedType)
                    nextLine()
                } else {
                    appendType(expr.allocatedType, expr.scope, true)
                    appendValueParams(graph, expr.paramsForLater)
                }
            }
            is SimpleCall -> {
                // Number.toX() needs to be converted to a cast
                val methodName = expr.methodName
                val done = when (expr.valueParameters.size) {
                    0 -> {
                        val castSymbol = when (methodName) {
                            "toInt" -> "(int) "
                            "toLong" -> "(long) "
                            "toFloat" -> "(float) "
                            "toDouble" -> "(double) "
                            "toByte" -> "(byte) "
                            "toShort" -> "(short) "
                            "toChar" -> "(char) "
                            else -> null
                        }
                        // todo append correct casting method...
                        if (castSymbol != null && expr.self.type in nativeNumbers) {
                            appendGetField(graph, expr.self)
                            builder.append(castSymbol)
                            true
                        } else if (expr.self.type == Types.Boolean && methodName == "not") {
                            appendGetField(graph, expr.self)
                            builder.append("i32.not")
                            nextLine()
                            true
                        } else false
                    }
                    1 -> {
                        val supportsType = expr.self.type in nativeNumbers
                        val symbol = when (methodName) {
                            "plus" -> "add"
                            "minus" -> "sub"
                            "times" -> "mul"
                            "div" -> "div"
                            "rem" -> "mod"
                            else -> null
                        }
                        if (supportsType && symbol != null) {
                            appendGetField(graph, expr.self)
                            appendGetField(graph, expr.valueParameters[0])
                            builder.append(getWASMType(expr.self.type).wasmName)
                                .append('.').append(symbol)
                            nextLine()
                            true
                        } else false
                    }
                    else -> false
                }
                if (!done) {
                    appendCallImpl(graph, expr)
                }
            }
            is SimpleGetField -> {
                // if this is a local variable, get that instead
                if (isLocalField(expr.self)) {

                    builder.append("local.get \$").append(expr.field.name)
                    nextLine()

                } else {
                    appendGetField(graph, expr.self)
                    appendFieldOffset(expr.self, expr.field)

                    builder.append(pointerType.wasmName).append(".add")
                    nextLine()

                    // todo choose correct instruction
                    builder.append("i32.load")
                    nextLine()
                }
            }
            is SimpleSetField -> {
                // if this is a local variable, set that instead
                if (isLocalField(expr.self)) {

                    appendGetField(graph, expr.value)
                    builder.append("local.set \$").append(expr.field.name)
                    nextLine()

                } else {
                    appendGetField(graph, expr.self)
                    appendFieldOffset(expr.self, expr.field)

                    builder.append(pointerType.wasmName).append(".add")
                    nextLine()

                    appendGetField(graph, expr.value)

                    // todo choose correct instruction
                    builder.append("i32.store")
                    nextLine()
                }

                // todo we must call copy until we support structs...
            }
            else -> {
                super.appendInstrImpl(graph, expr)
                nextLine()
            }
        }
    }

    fun isLocalField(self: SimpleField): Boolean {
        return self.type is ClassType && !self.type.clazz.isClassLike()
    }

    fun appendFieldOffset(self: SimpleField, field: Field) {
        val offset = getStruct(ClassSpecialization(self.type as ClassType))
            .getOffset(field)
        builder.append(pointerType.wasmName).append(".const ").append(offset)
            .append(" ;; ").append(field.ownerScope.pathStr).append('.').append(field.name)
        nextLine()
    }

    override fun appendValueParams(graph: SimpleGraph, valueParameters: List<SimpleField>, withBrackets: Boolean) {
        for (i in valueParameters.indices) {
            appendGetField(graph, valueParameters[i])
        }
    }

    override fun appendCallImpl(graph: SimpleGraph, expr: SimpleCall) {
        appendGetField(graph, expr.self)
        appendValueParams(graph, expr.valueParameters)

        // todo do inheritance/method-res is necessary
        builder.append("call \$").append(getMethodName(expr.methodSpec))
        nextLine()
    }

    fun appendCallImpl(graph: SimpleGraph, expr: SimpleSelfConstructor) {
        appendGetField(graph, expr.self)
        appendValueParams(graph, expr.valueParameters)

        // todo do inheritance/method-res is necessary
        builder.append("call \$").append(getMethodName(expr.methodSpec))
        nextLine()

        appendDrop()
    }

}