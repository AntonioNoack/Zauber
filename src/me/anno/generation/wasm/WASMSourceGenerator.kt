package me.anno.generation.wasm

import me.anno.generation.c.CSourceGenerator
import me.anno.generation.wasm.WASMType.Companion.anyRef
import me.anno.utils.CollectionUtils.partitionBy
import me.anno.utils.ListOfByteArrays
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.reverse.CodeReconstruction
import me.anno.zauber.ast.reverse.SimpleBranch
import me.anno.zauber.ast.reverse.SimpleLoop
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.expression.Expression
import me.anno.zauber.ast.simple.*
import me.anno.zauber.ast.simple.SimpleNode.Companion.isValue
import me.anno.zauber.ast.simple.SimpleNode.Companion.needsCopy
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
import me.anno.zauber.types.specialization.Specialization.Companion.noSpecialization
import java.io.File

/**
 * todo yield can be implemented by modifying graph + virtual class, and I think we can do that
 *
 * todo extend tests to have code-gen stages
 *
 * todo split value classes into their components (exploded local fields, exploded class properties)
 * todo value class methods are two-fold: with reference, with just values
 *
 * todo copy/adjust this class into JVM-bytecode
 * todo custom WASM-runtime, so we can execute code faster for testing
 *
 * directly encode binary WASM
 * like with generating JVM bytecode, we should convert simple-fields back to a stack, where possible -> not really necessary
 * */
class WASMSourceGenerator : CSourceGenerator() {

    companion object {
        const val CLASS_INDEX_NAME = "_type"
    }

    val functionTypes = HashMap<FunctionType, Int>()
    val typeList = ArrayList<WASMFuncTypeOrStruct>()

    val binary = WASMBinaryWriter()
    val importList = ArrayList<Pair<String, Int>>()
    val exportList = ArrayList<Pair<String, Int>>()

    val functionIndexMap = HashMap<MethodSpecialization, Int>()
    val functionIndexList = ArrayList<MethodSpecialization>()

    val globalNames = ArrayList<String>()
    val globalStructs = ArrayList<WASMType.Ref>()
    val objectGlobals = HashMap<Scope, Int>()
    lateinit var objectGetters: Map<Scope, Int>
    lateinit var objects: List<ClassSpecialization>

    val bodies = ListOfByteArrays(binary.out)
    val structs = HashMap<Pair<ClassSpecialization, Boolean>, WASMStruct>()

    fun registerMethods(data: DependencyData, mainMethod: Method) {
        // imported methods first
        functionIndexList.addAll(data.calledMethods)
        functionIndexList.partitionBy { method -> method.method.body != null }

        var hadImpl = false
        for (i in functionIndexList.indices) {
            val method = functionIndexList[i]
            functionIndexMap[method] = functionIndexMap.size
            if (method.method.body != null) {
                hadImpl = true
            } else {
                check(!hadImpl) { "Imports must be first" }
                val methodName = getMethodName(method)
                val funcType = getFunctionTypeIndex(method)
                importList.add(methodName to funcType)
            }
        }

        // register function type for main
        getMainMethodType(mainMethod)
    }

    fun defineObjectGetters(data: DependencyData) {
        // find constructed object-likes
        val objects = data.createdClasses.filter { it.clazz.isObjectLike() }
        val getters = HashMap<Scope, Int>(objects.size)
        for (i in objects.indices) {
            getters[objects[i].clazz] = i
        }
        this.objects = objects
        objectGetters = getters
    }

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {

        registerMethods(data, mainMethod)
        defineObjectGetters(data)
        depth++

        comment { builder.append("method imports") }
        for (method in functionIndexList) {
            if (method.method.body != null) continue
            appendMethodImport(method)
        }

        val methodImports = builder.toString()
        builder.clear()

        comment { builder.append("method implementations") }
        for (method in functionIndexList) {
            if (method.method.body == null) continue
            appendMethodCode(method)
        }

        comment { builder.append("object getters") }
        for (obj in objects) {
            appendObjectGetterCode(obj.clazz)
        }

        appendMainMethodCode(mainMethod)

        depth--

        val methodBodies = builder.toString()
        builder.clear()

        beginModule()
        writeStructTypes()
        writeMethodTypes()
        builder.append(methodImports)
        writeObjectGlobals()
        builder.append(methodBodies)
        endModule()

        dst.writeText(builder.toString())
        builder.clear()

        val start = binary.out.size
        binary.writeModuleHeader()
        binary.writeTypeSection(typeList)
        binary.writeImportSection(importList)
        val functionTypes = functionIndexList.subList(importList.size, functionIndexList.size)
            .map { method -> getFunctionTypeIndex(method) }
        val objectGetterTypes = objects.map { getObjectGetterFunctionTypeIndex(it.clazz) }
        val implFunctions = functionTypes + objectGetterTypes + getMainMethodType(mainMethod)
        check(implFunctions.size == bodies.size)
        binary.writeFunctionSection(implFunctions)
        binary.writeMemorySection()
        binary.writeGlobalSection(globalStructs)
        binary.writeExportSection(exportList)
        binary.writeCodeSection(bodies)
        binary.out.removeSection(0, start)
    }

    fun getMainMethodType(mainMethod: Method): Int {
        // could have args...
        return getFunctionTypeIndex(FunctionType(emptyList(), emptyList()))
    }

    fun appendMainMethodCode(mainMethod: Method) {
        val mainMethodIndex = functionIndexList.size + objects.size
        exportList.add("main" to mainMethodIndex)

        builder.append("(func main (export \"main\") (type $")
            .append(getMainMethodType(mainMethod)).append(")")
        depth++
        nextLine()

        declareFuncHasNoLocalFields()
        appendGetObjectInstance(mainMethod.ownerScope, root)
        callMethod(MethodSpecialization(mainMethod, noSpecialization))
        nextLine()

        appendDrop()

        builder.setLength(builder.length - 2)
        depth--
        builder.append(")")
        nextLine()

        binary.u8(WASMOpcode.END)
        bodies.finishBody()
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

        comment { builder.append("memory") }
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
        comment { builder.append("method types") }
        val functionTypeList = typeList
        for (i in functionTypeList.indices) {
            val type = functionTypeList[i] as? FunctionType ?: continue

            builder.append("(type \$t")
                .append(i)
                .append(" (func")

            for (param in type.params) {
                builder.append(" (param ")
                    .append(param.wasmName)
                    .append(')')
            }

            for (result in type.results) {
                builder.append(" (result ")
                    .append(result.wasmName)
                    .append(')')
            }

            builder.append("))")
            nextLine()
        }
    }

    fun writeStructTypes() {
        comment { builder.append("struct types") }
        for (struct in structs.values) {

            builder.append("(type $").append(struct.typeName)
            if (struct.superType != null) {
                builder.append(" (sub $").append(struct.superType.typeName)
            }

            builder.append(" (struct")

            for (property in struct.properties) {

                builder.append(" (field $").append(property.field?.name ?: CLASS_INDEX_NAME)
                    .append(" ")

                when (val t = property.wasmType) {
                    is WASMType.Ref -> builder.append(t.wasmName)
                    else -> builder.append(t.wasmName)
                }

                builder.append(")")
            }

            if (struct.superType != null) builder.append(")")
            builder.append("))")
            nextLine()
        }
    }

    fun getWASMType(type: Type): WASMType {
        return when (val type = resolveType(type)) {

            Types.Byte, Types.UByte,
            Types.Short, Types.UShort,
            Types.Int, Types.UInt -> WASMType.I32

            Types.Long, Types.ULong -> WASMType.I64

            Types.Float, Types.Half -> WASMType.F32
            Types.Double -> WASMType.F64

            is ClassType -> getStruct(ClassSpecialization(type), false).type
            else -> anyRef
        }
    }

    fun getFunctionTypeIndex(method: MethodSpecialization): Int {
        method.specialization.use {
            val method = method.method
            method.scope[ScopeInitType.CODE_GENERATION]

            val params = ArrayList<WASMType>(
                1 + (if (method.explicitSelfType) 1 else 0) +
                        method.valueParameters.size
            )

            // self:
            params.add(getWASMType(method.ownerScope.typeWithArgs))

            if (method.explicitSelfType) {
                val selfType = method.selfType!!
                params.add(getWASMType(selfType))
            }

            for (param in method.valueParameters) {
                params.add(getWASMType(param.type))
            }

            // todo throwing methods could return the value as an extra return value...
            //  or we finally use WASM exceptions :)

            val returnType = method.returnType
                ?: throw IllegalStateException("$method misses return type")
            val returnTypes = listOf(getWASMType(returnType))
            val functionType = FunctionType(params, returnTypes)
            return getFunctionTypeIndex(functionType)
        }
    }

    fun getFunctionTypeIndex(functionType: FunctionType): Int {
        return functionTypes.getOrPut(functionType) {
            typeList.add(functionType)
            typeList.lastIndex
        }
    }

    // we cannot just quit -> removing try-catch
    override fun appendMethods(
        classScope: Scope, className: String,
        methods: Collection<MethodSpecialization>, headerOnly: Boolean
    ) {
        for (method0 in methods) {
            val method = method0.method
            if (method !is Method) continue
            if (method.scope.parent != classScope) {
                // an inherited method -> skip, because it's already defined in the parent
                continue
            }

            appendMethod(classScope, className, method0, headerOnly)
        }
    }

    fun appendMethodHeader(typeIndex: Int, methodName: String, method0: MethodSpecialization, export: Boolean) {
        val method = method0.method
        val functionIndex = functionIndexMap[method0]!!
        appendMethodHeader(
            typeIndex, methodName, functionIndex, method.explicitSelfType,
            method.valueParameters, export
        )
    }

    fun appendMethodHeader(
        typeIndex: Int, methodName: String, functionIndex: Int,
        explicitSelfType: Boolean, valueParameters: List<Parameter>,
        isExportedFunction: Boolean
    ) {
        val type = typeList[typeIndex] as FunctionType

        builder.append("(func $").append(methodName)

        if (isExportedFunction) {
            builder.append(" (export \"").append(methodName).append("\")")
            exportList.add(methodName to functionIndex)
        }

        builder.append(" (type \$t").append(typeIndex).append(")")
        if (type.params.isNotEmpty()) {
            appendParamWithName(type.params[0], "this")
            var j = 1
            if (explicitSelfType) {
                appendParamWithName(type.params[1], "__self")
                j++
            }
            for (i in valueParameters.indices) {
                val param = valueParameters[i]
                appendParamWithName(type.params[j + i], param.name)
            }
        } // else object getter

        // return types
        for (resultType in type.results) {
            builder.append(" (result ").append(resultType.wasmName)
                .append(")")
        }

        depth++
        nextLine()
    }

    fun appendParamWithName(type: WASMType, name: String) {
        builder.append(" (param \$").append(name)
            .append(' ').append(type.wasmName).append(")")
    }

    override fun getMethodName(method: MethodSpecialization): String {
        val base = if (method.method is Constructor) "_init_" else super.getMethodName0(method)
        return "${method.method.ownerScope.pathStr.replace('.', '_')}_${base}_${hashMethodParameters(method)}"
    }

    fun appendMethodCode(method0: MethodSpecialization) {
        val type = getFunctionTypeIndex(method0)
        val (method, spec) = method0
        spec.use {
            appendMethodHeader(type, getMethodName(method0), method0, true)

            if (method !is Constructor || method.ownerScope.typeWithArgs !in nativeNumbers) {
                val body = method.body!!
                val context = ResolutionContext(method.selfType, spec, true, null)
                appendCode(context, method, body, false)
            } else {
                // we would get issues, if we tried to call super(), because 'this' is not a reference
                declareFuncHasNoLocalFields()
            }

            if (method is Constructor) {
                // return is typically missing
                if (method.ownerScope == Types.Unit.clazz) appendGetThis() // todo why is this not working???
                else appendGetObjectInstance(Types.Unit.clazz, method.scope)
            }

            // close body
            builder.setLength(builder.length - 2)
            builder.append(")")
            binary.u8(WASMOpcode.END)
            depth--
            nextLine()

            bodies.finishBody()
        }
    }

    fun getObjectGetterFunctionTypeIndex(objectScope: Scope): Int {
        val returnType = objectScope.typeWithArgs
        val functionType = FunctionType(emptyList(), listOf(getWASMType(returnType)))
        return getFunctionTypeIndex(functionType)
    }

    fun appendObjectGetterCode(objectScope: Scope) {
        val type = getObjectGetterFunctionTypeIndex(objectScope)
        noSpecialization.use {
            appendMethodHeader(
                type, getObjectGetterName(objectScope),
                getObjectGetterFunctionIndex(objectScope), false,
                emptyList(), true
            )

            declareFuncHasNoLocalFields()
            appendGetObjectInstanceImpl(objectScope)

            // close body
            builder.setLength(builder.length - 2)
            builder.append(")")
            binary.u8(WASMOpcode.END)
            depth--
            nextLine()

            bodies.finishBody()
        }
    }

    fun appendMethodImport(method: MethodSpecialization) {
        // (import "env" "print_i32" (func $print_i32 (param i32)))
        val type = getFunctionTypeIndex(method)
        method.specialization.use {
            val methodName = getMethodName(method)
            builder.append("(import \"env\" \"").append(methodName).append("\" ")
            appendMethodHeader(type, methodName, method, false)
            trimWhitespaceAtEnd()
            builder.append("))")
            depth--
            nextLine()
        }
    }

    override fun appendCode(context: ResolutionContext, method: MethodLike, body: Expression, skipSuperCall: Boolean) {
        val method1 = MethodSpecialization(method, context.specialization)
        val graph = ASTSimplifier.simplify(method1)
        if (skipSuperCall) graph.removeSuperCalls()
        graph.removeWriteOnlyFields(alsoObjects = true, alsoThis = true)

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

        // write all code
        appendSimplifiedAST(graph, graph.startBlock)
    }

    fun declareFuncHasNoLocalFields() {
        binary.u32(0)
    }

    fun declareLocalFieldsAsVariables(graph: SimpleGraph) {
        // todo we must also include mutable fields...
        val offset = getLocalFieldOffset(graph)
        val mutableFields = collectMutableFields(graph, offset)

        val types = ArrayList<WASMType>()
        for (field in graph.fields) {
            val type = getWASMType(field.type)
            builder.append("(local \$tmp").append(field.id)
                .append(' ').append(type.wasmName).append(')')
            types.add(type)
            nextLine()
        }
        for (field in mutableFields) {
            // todo ensure unique names
            field.scope[ScopeInitType.AFTER_RESOLVE_TYPES]

            val valueType = field.valueType
                ?: throw IllegalStateException("Missing valueType for $field")

            val type = getWASMType(valueType)
            builder.append("(local $").append(field.name)
                .append(' ').append(type.wasmName).append(')')
            types.add(type)
            nextLine()
        }

        // todo we must reorder all field indices...
        /*val grouped = graph.fields.groupBy { getWASMType(it.type) }
        binary.u32(grouped.size)
        for ((type, vars) in grouped) {
            binary.u32(vars.size)
            binary.u8(type.valType) // assumes mapping exists
        }*/
        // hack:
        binary.u32(types.size)
        for (type in types) {
            binary.u32(1)
            binary.writeValueType(type)
        }
    }

    lateinit var mutableFields: Map<Field, Int>

    fun collectMutableFields(graph: SimpleGraph, offset: Int): List<Field> {
        val fieldMap = HashMap<Field, Int>()
        val fieldList = ArrayList<Field>()
        val offset = offset + graph.fields.size
        val parameters = graph.method.valueParameters
        val thisOffset = 1 + if (graph.method.explicitSelfType) 1 else 0
        for (block in graph.nodes) {
            for (expr in block.instructions) {
                when (expr) {
                    is SimpleGetField, is SimpleSetField -> {
                        val fieldSelf = when (expr) {
                            is SimpleGetField -> expr.self
                            is SimpleSetField -> expr.self
                            else -> error("Unreachable")
                        }
                        if (isLocalField(fieldSelf)) {
                            val field = when (expr) {
                                is SimpleGetField -> expr.field
                                is SimpleSetField -> expr.field
                                else -> error("Unreachable")
                            }

                            val parameterIndex = parameters.indexOf(field.byParameter)
                            fieldMap.getOrPut(field) {
                                if (parameterIndex >= 0) {
                                    parameterIndex + thisOffset
                                } else {
                                    fieldList.add(field)
                                    fieldList.lastIndex + offset
                                }
                            }
                        }
                    }
                }
            }
        }
        mutableFields = fieldMap
        return fieldList
    }

    override fun appendInstrPrefix(graph: SimpleGraph, expr: SimpleInstruction) {
        // nothing required
    }

    fun appendDrop() {
        builder.append("drop")
        binary.drop()
        nextLine()
    }

    fun appendUnreachable() {
        builder.append("unreachable")
        binary.unreachable()
        nextLine()
    }

    override fun appendAssign(graph: SimpleGraph, expression: SimpleAssignment) {
        // todo remove non-read fields from graph
        builder.append("local.set \$tmp").append(expression.dst.id)
        binary.localSet(expression.dst.id + getLocalFieldOffset(graph))
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

    fun writeObjectGlobals() {
        comment { builder.append("globals") }
        for ((scope, index) in objectGlobals.entries.sortedBy { it.value }) {
            val struct = getStruct(ClassSpecialization(scope, noSpecialization), true)
            builder.append("(global $")
                .append(globalNames[index])
                .append(" (mut (ref null $")
                .append(struct.typeName)
                .append(")) (ref.null $")
                .append(struct.typeName)
                .append("))")
            nextLine()
        }
    }

    val classIndexProp = listOf(WASMProperty(null, WASMType.I32, 0))

    fun getStruct(classSpecialization: ClassSpecialization, isNullable: Boolean): WASMStruct {
        check(classSpecialization.clazz.isClassLike()) {
            "Invalid struct: $classSpecialization"
        }
        return structs.getOrPut(classSpecialization to isNullable) {
            val (clazz, spec) = classSpecialization
            spec.use {

                val superType = run {
                    if (isNullable) {
                        getStruct(classSpecialization, false)
                    } else {
                        val superType0 = classSpecialization.superType
                        if (superType0 != null) getStruct(superType0, false) else null
                    }
                }

                val props = clazz.fields
                    .filter { isStoredField(it) }
                    .mapIndexed { index, field ->
                        field.ownerScope[ScopeInitType.AFTER_RESOLVE_TYPES]

                        val type = getWASMType(
                            field.valueType
                                ?: throw IllegalStateException("Missing valueType for $field")
                        )
                        WASMProperty(field, type, classIndexProp.size + index)
                    }

                val typeIndex = typeList.size
                var typeName = getClassName(clazz, spec)
                if (isNullable) typeName += "?"
                val struct = WASMStruct(superType, typeIndex, typeName, classIndexProp + props, isNullable)
                typeList.add(struct)
                struct
            }
        }
    }

    override fun getClassName(scope: Scope, specialization: Specialization): String {
        return scope.pathStr.replace('.', '_') + createSpecializationSuffix(specialization)
    }

    fun appendGetObjectInstanceImpl(objectScope: Scope) {
        val structNullable = getStruct(ClassSpecialization(objectScope, noSpecialization), isNullable = true)
        val globalIndex = objectGlobals.getOrPut(objectScope) {
            globalNames.add("global_${objectScope.pathStr.replace('.', '_')}")
            globalStructs.add(structNullable.type)
            objectGlobals.size
        }

        globalGet(globalIndex)
        nextLine()

        builder.append("ref.is_null")
        binary.u8(WASMOpcode.REF_IS_NULL)
        nextLine()

        emptyResultIf()

        builder.append("struct.new_default $").append(structNullable.typeName)
        binary.structNewDefault(structNullable.typeIndex)
        nextLine()

        globalSet(globalIndex)
        builder.append(' ')
        globalGet(globalIndex)
        nextLine()

        val constructor = objectScope
            .getOrCreatePrimaryConstructorScope()
            .selfAsConstructor!!

        castNonNull()
        callMethod(MethodSpecialization(constructor, noSpecialization))
        nextLine()

        appendDrop()
        emptyResultEnd()

        globalGet(globalIndex)
        nextLine()

        castNonNull()
    }

    fun castNonNull() {
        builder.append("ref.as_non_null")
        binary.u8(WASMOpcode.REF_AS_NON_NULL)
        nextLine()
    }

    fun getObjectGetterName(objectScope: Scope): String {
        return "obj_${objectScope.pathStr.replace('.', '_')}"
    }

    fun getObjectGetterFunctionIndex(objectScope: Scope): Int {
        return functionIndexList.size + objectGetters[objectScope]!!
    }

    override fun appendGetObjectInstance(objectScope: Scope, exprScope: Scope) {
        builder.append("call $").append(getObjectGetterName(objectScope))
        binary.call(getObjectGetterFunctionIndex(objectScope))
        nextLine()
    }

    fun emptyResultIf() {
        builder.append("(if (then")
        depth++
        nextLine()

        binary.u8(WASMOpcode.IF)
        binary.u8(0x40) // empty block
    }

    fun emptyResultElse() {
        builder.append(") (else")
        nextLine()

        binary.u8(WASMOpcode.ELSE)
    }

    fun emptyResultEnd() {
        builder.append("))")
        depth--
        nextLine()

        binary.u8(WASMOpcode.END)
    }

    fun globalGet(globalIndex: Int) {
        builder.append("global.get $").append(globalNames[globalIndex])
        binary.globalGet(globalIndex)
    }

    fun globalSet(globalIndex: Int) {
        builder.append("global.set $").append(globalNames[globalIndex])
        binary.globalSet(globalIndex)
    }

    fun insideObjectConstructor(graph: SimpleGraph, objectScope: Scope): Boolean {
        return objectScope.getOrCreatePrimaryConstructorScope().selfAsConstructor == graph.method
    }

    fun getLocalFieldOffset(graph: SimpleGraph): Int {
        var offset = 1 // 'this'
        if (graph.method.explicitSelfType) offset++ // 'self'
        val numParams = graph.method.valueParameters.size
        return offset + numParams
    }

    fun appendGetThis() {
        builder.append("local.get \$this")
        binary.localGet(0)
        nextLine()
    }

    fun appendGetField(graph: SimpleGraph, field: SimpleField) {
        if (field.isOwnerThis(graph)) {
            appendGetThis()
        } else if (field.isObjectLike()) {
            val objectScope = (field.type as ClassType).clazz
            if (insideObjectConstructor(graph, objectScope)) {
                appendGetThis()
            } else {
                appendGetObjectInstance(objectScope, graph.method.scope)
            }
        } else {
            var field = field
            while (true) {
                field = field.mergeInfo?.dst ?: break
            }
            builder.append("local.get \$tmp").append(field.id)
            binary.localGet(field.id + getLocalFieldOffset(graph))
            nextLine()
        }
    }

    fun i32Const(value: Int) {
        builder.append("i32.const ").append(value)
        binary.i32Const(value)
    }

    fun i64Const(value: Long) {
        builder.append("i64.const ").append(value)
        binary.i64Const(value)
    }

    fun f32Const(value: Float) {
        builder.append("f32.const ").append(value)
        binary.f32Const(value)
    }

    fun f64Const(value: Double) {
        builder.append("f64.const ").append(value)
        binary.f64Const(value)
    }

    fun ret() {
        builder.append("return")
        binary.ret()
    }

    override fun appendInstrImpl(graph: SimpleGraph, expr: SimpleInstruction) {
        when (expr) {
            is SimpleNumber -> {
                when (expr.dst.type) {
                    Types.Byte, Types.UByte, Types.Short, Types.UShort,
                    Types.Int, Types.UInt -> i32Const(expr.base.asInt.toInt())
                    Types.Long, Types.ULong -> i64Const(expr.base.asInt)
                    Types.Float, Types.Half -> f32Const(expr.base.asFloat.toFloat())
                    Types.Double -> f64Const(expr.base.asFloat)
                    else -> throw NotImplementedError("Append $expr")
                }
                nextLine()
            }
            is SimpleDeclaration -> {
                // is this supported in wasm??? obviously not
                /*builder.append("(local $").append(expr.name)
                    .append(' ').append(getWASMType(expr.type).wasmName)
                    .append(")")
                nextLine()*/
                // just skipped, they need unique names anyway
            }
            is SimpleBranch -> {
                appendGetField(graph, expr.condition)
                emptyResultIf()
                /**/appendSimplifiedAST(graph, expr.ifTrue)
                emptyResultElse()
                /**/appendSimplifiedAST(graph, expr.ifFalse)
                emptyResultEnd()
            }
            is SimpleLoop -> {
                TODO("implement loop")
            }
            is SimpleReturn -> {
                appendGetField(graph, expr.field)
                ret()
                nextLine()
            }
            is SimpleSelfConstructor -> {
                appendCallImpl(graph, expr)
            }
            is SimpleAllocateInstance -> {
                // todo test nullable variables
                // this allocation is a ClassType, so it cannot be null ever
                val struct = getStruct(ClassSpecialization(expr.allocatedType), false)
                if (!expr.allocatedType.isValue()) {
                    /*for (param in expr.paramsForLater) {
                        appendGetField(graph, param)
                    }*/
                    builder.append("struct.new $").append(struct.typeName)
                    nextLine()
                } else {
                    appendType(expr.allocatedType, expr.scope, true)
                    // appendValueParams(graph, expr.paramsForLater)
                }
                // we will use the struct in both cases for now
                binary.structNewDefault(struct.typeIndex)
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
                            TODO("find cast-instr")
                            true
                        } else if (expr.self.type == Types.Boolean && methodName == "not") {
                            appendGetField(graph, expr.self)
                            builder.append("i32.eqz")
                            binary.u8(WASMOpcode.I32_EQZ)
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
                            val type = resolveType(expr.self.type)
                            builder.append(getWASMType(type).wasmName)
                                .append('.').append(symbol)
                            binary.u8(getSimpleMathOp(type, symbol))
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
                if (isLocalField(expr.self)) {
                    builder.append("local.get $").append(expr.field.name)
                    binary.localGet(mutableFields[expr.field]!!)
                    nextLine()
                } else {

                    // load field owner
                    appendGetField(graph, expr.self)

                    val selfType = expr.self.type as ClassType
                    if (selfType !in nativeNumbers) {
                        val struct = getStruct(ClassSpecialization(selfType), isNullable = false)
                        val fieldIndex = struct.getIndex(expr.field)
                        builder.append("struct.get $")
                            .append(struct.typeName).append(' ')
                            .append(fieldIndex)
                        binary.structGet(struct.typeIndex, fieldIndex)
                        nextLine()
                    } else {
                        // field must be 'content'
                        check(expr.field.name == "content")
                    }
                }
            }
            is SimpleSetField -> {
                val needsCopy = expr.value.type.needsCopy()
                if (isLocalField(expr.self)) {
                    appendGetField(graph, expr.value)
                    if (needsCopy) appendCopy(graph, expr)

                    builder.append("local.set $").append(expr.field.name)
                    binary.localSet(mutableFields[expr.field]!!)
                    nextLine()

                } else {

                    appendGetField(graph, expr.self)
                    appendGetField(graph, expr.value)
                    if (needsCopy) appendCopy(graph, expr)

                    val selfType = expr.self.type as ClassType
                    val struct = getStruct(ClassSpecialization(selfType), isNullable = false)

                    val fieldIndex = struct.getIndex(expr.field)
                    builder.append("struct.set $")
                        .append(struct.typeName).append(' ')
                        .append(fieldIndex)
                    binary.structSet(struct.typeIndex, fieldIndex)
                    nextLine()
                }
            }
            else -> throw NotImplementedError("Implement writing $expr (${expr.javaClass.simpleName})")
        }
    }

    override fun appendCopy(graph: SimpleGraph, expr: SimpleSetField) {
        val valueType = expr.value.type as ClassType
        val method = valueType.clazz.methods0.first { it.name == "copy" && it.valueParameters.isEmpty() }
        val spec = Specialization(valueType)
        callMethod(MethodSpecialization(method, spec))
        nextLine()
    }

    fun getSimpleMathOp(type: Type, symbol: String): Int {
        return when (type) {
            Types.Int -> when (symbol) {
                "add" -> WASMOpcode.I32_ADD
                "sub" -> WASMOpcode.I32_SUB
                "mul" -> WASMOpcode.I32_MUL
                "div" -> WASMOpcode.I32_DIV_S
                "mod" -> WASMOpcode.I32_REM_S
                else -> null
            }
            Types.UInt -> when (symbol) {
                "add" -> WASMOpcode.I32_ADD
                "sub" -> WASMOpcode.I32_SUB
                "mul" -> WASMOpcode.I32_MUL
                "div" -> WASMOpcode.I32_DIV_U
                "mod" -> WASMOpcode.I32_REM_U
                else -> null
            }
            Types.Long -> when (symbol) {
                "add" -> WASMOpcode.I64_ADD
                "sub" -> WASMOpcode.I64_SUB
                "mul" -> WASMOpcode.I64_MUL
                "div" -> WASMOpcode.I64_DIV_S
                "mod" -> WASMOpcode.I64_REM_S
                else -> null
            }
            Types.ULong -> when (symbol) {
                "add" -> WASMOpcode.I64_ADD
                "sub" -> WASMOpcode.I64_SUB
                "mul" -> WASMOpcode.I64_MUL
                "div" -> WASMOpcode.I64_DIV_U
                "mod" -> WASMOpcode.I64_REM_U
                else -> null
            }
            Types.Float, Types.Half -> when (symbol) {
                "add" -> WASMOpcode.F32_ADD
                "sub" -> WASMOpcode.F32_SUB
                "mul" -> WASMOpcode.F32_MUL
                "div" -> WASMOpcode.F32_DIV
                "mod" -> WASMOpcode.F32_REM
                else -> null
            }
            Types.Double -> when (symbol) {
                "add" -> WASMOpcode.F64_ADD
                "sub" -> WASMOpcode.F64_SUB
                "mul" -> WASMOpcode.F64_MUL
                "div" -> WASMOpcode.F64_DIV
                "mod" -> WASMOpcode.F64_REM
                else -> null
            }
            else -> null
        } ?: throw NotImplementedError("Missing opcode for $type.$symbol")
    }

    fun isLocalField(self: SimpleField): Boolean {
        return self.type is ClassType && !self.type.clazz.isClassLike()
    }

    override fun appendValueParams(graph: SimpleGraph, valueParameters: List<SimpleField>, withBrackets: Boolean) {
        for (i in valueParameters.indices) {
            appendGetField(graph, valueParameters[i])
        }
    }

    override fun appendCallImpl(graph: SimpleGraph, expr: SimpleCall) {
        appendGetField(graph, expr.self)
        appendValueParams(graph, expr.valueParameters)

        if (expr.methods !is FullMap) {
            if (expr.sample.ownerScope.isInterface()) {
                TODO("interface method-res")
            } else {
                TODO("child-class method-res")
            }
        }
        callMethod(expr.methodSpec)
        nextLine()
    }

    fun appendCallImpl(graph: SimpleGraph, expr: SimpleSelfConstructor) {
        appendGetField(graph, expr.self)
        appendValueParams(graph, expr.valueParameters)

        callMethod(expr.methodSpec)
        nextLine()

        appendDrop()
    }

    fun callMethod(method: MethodSpecialization) {
        val index = functionIndexMap[method]
            ?: throw IllegalStateException("Missing $method, cannot call it")
        builder.append("call $").append(getMethodName(method))
        binary.call(index)
    }

}