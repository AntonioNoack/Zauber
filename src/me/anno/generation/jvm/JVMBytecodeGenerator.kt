package me.anno.generation.jvm

import me.anno.generation.java.JavaSourceGenerator
import me.anno.zauber.SpecialFieldNames.OBJECT_FIELD_NAME
import me.anno.zauber.ast.rich.expression.CompareType
import me.anno.zauber.ast.rich.expression.constants.SpecialValue
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.member.MethodLike
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleGraph
import me.anno.zauber.ast.simple.controlflow.SimpleReturn
import me.anno.zauber.ast.simple.controlflow.SimpleThrow
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.ast.simple.fields.SimpleGetLocalField
import me.anno.zauber.ast.simple.fields.SimpleInstruction
import me.anno.zauber.ast.simple.fields.SimpleSetLocalField
import me.anno.zauber.expansion.DependencyData
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Specialization.Companion.noSpecialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.impl.arithmetic.NullType
import me.anno.zauber.types.impl.arithmetic.UnionType
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * JVM bytecode backend.
 *
 * Like WASM, we append binary directly; but like LLVM (future), we skip CodeReconstruction and
 * just linearize the SimpleGraph CFG into blocks + branches.
 *
 * For debugging, we also write a ".java" text file next to each ".class" containing Java-style
 * method headers, but bytecode-style instruction mnemonics in the body.
 */
class JVMBytecodeGenerator : JavaSourceGenerator() {

    override fun getExtension(headerOnly: Boolean): String = "class"

    val binary = JVMBytecodeWriter()

    override fun generateCode(dst: File, data: DependencyData, mainMethod: Method) {
        val classFolder = File(dst, "target")
        if (classFolder.exists()) classFolder.deleteRecursively()
        classFolder.mkdirs()

        generateAllClasses(classFolder, data, mainMethod)
        createJar(File(dst, "Zauber.jar"), classFolder)
    }

    private fun createJar(jarFile: File, classFolder: File) {
        jarFile.outputStream().buffered().use { fos ->
            JarOutputStream(fos).use { jar ->
                addManifest(jar)
                addDirectory(jar, classFolder)
            }
        }
    }

    private fun addManifest(jar: JarOutputStream) {
        val manifest = java.util.jar.Manifest()
        manifest.mainAttributes.apply {
            put(Attributes.Name.MANIFEST_VERSION, "1.0")
            put(Attributes.Name.MAIN_CLASS, "zauber.LaunchZauber")
            put(Attributes.Name.IMPLEMENTATION_TITLE, "minimal")
            put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0-SNAPSHOT")
        }
        jar.putNextEntry(JarEntry("META-INF/MANIFEST.MF"))
        manifest.write(jar)
        jar.closeEntry()
    }

    private fun addDirectory(jar: JarOutputStream, root: File) {
        for (file in root.walkTopDown()) {
            if (file.isDirectory) continue
            if (file.extension != "class") continue
            val relative = file.toRelativeString(root).replace('\\', '/')
            jar.putNextEntry(JarEntry(relative))
            file.inputStream().use { it.transferTo(jar) }
            jar.closeEntry()
        }
    }

    internal data class GenClass(
        val scope: Scope,
        val specialization: Specialization,
        val methods: Collection<Specialization>,
        val fields: Collection<Specialization>,
        val className: String,
        val packageScope: Scope,
        val internalName: String,
    )

    private fun generateAllClasses(classFolder: File, data: DependencyData, mainMethod: Method) {
        // same grouping as JavaSourceGenerator.generateCodeImpl(), but producing .class instead of .java
        val methodsByClass = data.calledMethods.groupBy { methodSpec ->
            val owner = methodSpec.method.ownerScope
            owner to methodSpec.typeParameters.filterByGenerics { it.scope == owner }
        }
        val fieldsByClass = (data.getFields + data.setFields).groupBy { fieldSpec ->
            val owner = fieldSpec.field.ownerScope
            owner to fieldSpec.typeParameters.filterByGenerics { it.scope == owner }
        }
        val classes1 = data.createdClasses.map { it.clazz to it.typeParameters }
        val classes = (methodsByClass.keys + fieldsByClass.keys + classes1)
            .filter { it.first.isClassLike() }
            .distinct()

        val generated = ArrayList<GenClass>(classes.size + 1)
        for (clazz in classes) {
            val (scope, typeParams) = clazz
            val classSpec = Specialization(scope, typeParams)
            scope[ScopeInitType.CODE_GENERATION]
            val methods = methodsByClass[clazz] ?: emptyList()
            val fields = fieldsByClass[clazz] ?: emptyList()
            val (name, packageScope) = getNameAndScope(scope, classSpec)
            val internalName = (packageScope.path + name).joinToString("/")
            generated.add(GenClass(scope, classSpec, methods, fields, name, packageScope, internalName))
        }

        // LaunchZauber entry point (manifest main-class expects this)
        run {
            val (bytes, dbg) = buildLaunchZauberClass(mainMethod)
            writeClassAndDebug(classFolder, "zauber/LaunchZauber", bytes, dbg)
        }

        // Zauber classes
        for (gen in generated) {
            val (bytes, dbg) = gen.specialization.use { buildZauberClass(gen) }
            writeClassAndDebug(classFolder, gen.internalName, bytes, dbg)
        }
    }

    private fun writeClassAndDebug(classFolder: File, internalName: String, bytes: ByteArray, debugJava: String) {
        val classFile = File(classFolder, "$internalName.class")
        classFile.parentFile.mkdirs()
        classFile.writeBytes(bytes)

        val dbgFile = File(classFolder, "$internalName.java")
        dbgFile.writeText(debugJava)
    }

    private fun buildLaunchZauberClass(mainMethod: Method): Pair<ByteArray, String> {
        val cp = ConstantPool()
        val thisInternal = "zauber/LaunchZauber"
        val thisClass = cp.clazz(thisInternal)
        val superClass = cp.clazz("java/lang/Object")
        val codeUtf8 = cp.utf8("Code")

        val methods = ArrayList<MethodInfo>(2)

        // <init>()
        run {
            val code = JvmCodeBuilder(cp)
            code.aload0()
            code.invokespecial("java/lang/Object", "<init>", "()V")
            code.ret()
            val codeAttr = binary.buildCodeAttributeInfo(8, 1, code.finish())
            methods.add(
                MethodInfo(
                    Opcodes.ACC_PUBLIC,
                    cp.utf8("<init>"),
                    cp.utf8("()V"),
                    listOf(AttributeInfo(codeUtf8, codeAttr))
                )
            )
        }

        // public static void main(String[] args)
        run {
            val code = JvmCodeBuilder(cp)

            // Invoke the compiled main-method via the object-like singleton field.
            val ownerInternal = internalNameOf(mainMethod.ownerScope.typeWithArgs)

            code.getstatic(ownerInternal, OBJECT_FIELD_NAME, "L$ownerInternal;")
            val targetDesc = methodDescriptorOf(mainMethod, noSpecialization, mainMethod.ownerScope)
            code.invokevirtual(ownerInternal, mainMethod.name, targetDesc)
            code.pop()
            code.ret()

            val codeAttr = binary.buildCodeAttributeInfo(32, 1, code.finish())
            methods.add(
                MethodInfo(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC,
                    cp.utf8("main"),
                    cp.utf8("([Ljava/lang/String;)V"),
                    listOf(AttributeInfo(codeUtf8, codeAttr))
                )
            )
        }

        val bytes = JVMBytecodeWriter().run {
            writeClassFile(
                minor = 0,
                major = 50, // Java 6: no StackMapTable required
                constantPool = cp,
                accessFlags = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
                thisClass = thisClass,
                superClass = superClass,
                interfaces = IntArray(0),
                fields = emptyList(),
                methods = methods,
                attributes = emptyList()
            )
            out.toByteArray()
        }

        val debugText = buildDebugText(
            packageName = "zauber",
            className = "LaunchZauber",
            methods = listOf(
                DebugMethod(
                    "public LaunchZauber()",
                    listOf("ALOAD_0", "INVOKESPECIAL java/lang/Object.<init>()V", "RETURN")
                ),
                DebugMethod("public static void main(String[] args)", listOf("...", "RETURN"))
            )
        )
        return bytes to debugText
    }

    private fun buildZauberClass(gen: GenClass): Pair<ByteArray, String> {
        val cp = ConstantPool()
        val thisClass = cp.clazz(gen.internalName)
        val superInternalName = getSuperInternalName(gen.scope)
        val superClass = cp.clazz(superInternalName)
        val codeUtf8 = cp.utf8("Code")

        val fields = ArrayList<FieldInfo>()
        val methods = ArrayList<MethodInfo>()
        val debugMethods = ArrayList<DebugMethod>()

        // Object-like scopes get: public static final <This> __object__ and <clinit> initializer.
        if (gen.scope.isObjectLike()) {
            fields.add(
                FieldInfo(
                    Opcodes.ACC_PUBLIC or Opcodes.ACC_STATIC or Opcodes.ACC_FINAL,
                    cp.utf8(OBJECT_FIELD_NAME),
                    cp.utf8("L${gen.internalName};"),
                    emptyList()
                )
            )
            run {
                val code = JvmCodeBuilder(cp)
                code.new0(gen.internalName)
                code.dup()
                code.invokespecial(gen.internalName, "<init>", "()V")
                code.putstatic(gen.internalName, OBJECT_FIELD_NAME, "L${gen.internalName};")
                code.ret()
                val codeAttr = binary.buildCodeAttributeInfo(16, 0, code.finish())
                methods.add(
                    MethodInfo(
                        Opcodes.ACC_STATIC,
                        cp.utf8("<clinit>"),
                        cp.utf8("()V"),
                        listOf(AttributeInfo(codeUtf8, codeAttr))
                    )
                )
                debugMethods.add(
                    DebugMethod(
                        "static {}",
                        listOf(
                            "NEW ${gen.internalName}",
                            "DUP",
                            "INVOKESPECIAL ${gen.internalName}.<init>()V",
                            "PUTSTATIC ${gen.internalName}.$OBJECT_FIELD_NAME : L${gen.internalName};",
                            "RETURN"
                        )
                    )
                )
            }
        }

        // Backing fields
        val allowFinalFields = !gen.scope.isValueType() && gen.methods.any { it.method is Constructor }
        for (fieldSpec in gen.fields) {
            val field = fieldSpec.field
            if (!isStoredField(field)) continue
            fieldSpec.use {
                val acc = Opcodes.ACC_PUBLIC or (if (!field.isMutable && allowFinalFields) Opcodes.ACC_FINAL else 0)
                val valueType = resolveType((field.valueType ?: Types.NullableAny).resolve(gen.scope))
                fields.add(
                    FieldInfo(
                        acc,
                        cp.utf8(field.newName),
                        cp.utf8(descriptorOf(valueType, gen.scope)),
                        emptyList()
                    )
                )
            }
        }

        if (gen.scope == Types.Array.clazz) {
            val elemType = resolveType(gen.specialization.typeParameters[0])
            fields.add(
                FieldInfo(
                    Opcodes.ACC_PRIVATE or Opcodes.ACC_FINAL,
                    cp.utf8("content"),
                    cp.utf8(arrayDescriptorOf(elemType, gen.scope)),
                    emptyList()
                )
            )
        }

        // Constructors
        val ctors = gen.methods.filter { it.method is Constructor }
        if (ctors.isNotEmpty()) {
            for (ctorSpec in ctors) {
                val ctor = ctorSpec.method as Constructor
                val (m, dbg) = buildConstructor(gen, ctorSpec, ctor, cp, codeUtf8)
                methods.add(m)
                debugMethods.add(dbg)
            }
        } else if (gen.scope.scopeType != ScopeType.INTERFACE && !gen.scope.isObjectLike()) {
            val (m, dbg) = buildDefaultConstructor(gen, cp, codeUtf8, superInternalName)
            methods.add(m)
            debugMethods.add(dbg)
        }

        // Methods
        for (methodSpec in gen.methods) {
            val method = methodSpec.method
            if (method !is Method) continue
            if (method.scope.parent != gen.scope) continue // skip inherited
            val (m, dbg) = buildMethod(gen, methodSpec, method, cp, codeUtf8)
            methods.add(m)
            if (dbg != null) debugMethods.add(dbg)
        }

        val bytes = JVMBytecodeWriter().run {
            writeClassFile(
                minor = 0,
                major = 50,
                constantPool = cp,
                accessFlags = Opcodes.ACC_PUBLIC or Opcodes.ACC_SUPER,
                thisClass = thisClass,
                superClass = superClass,
                interfaces = IntArray(0),
                fields = fields,
                methods = methods,
                attributes = emptyList()
            )
            out.toByteArray()
        }

        val pkg = gen.internalName.substringBeforeLast('/', missingDelimiterValue = "").replace('/', '.')
        val debugText = buildDebugText(pkg, gen.className, debugMethods)
        return bytes to debugText
    }

    private fun buildDefaultConstructor(
        gen: GenClass,
        cp: ConstantPool,
        codeUtf8: Int,
        superInternalName: String
    ): Pair<MethodInfo, DebugMethod> {
        val code = JvmCodeBuilder(cp)
        code.aload0()
        code.invokespecial(superInternalName, "<init>", "()V")
        code.ret()
        val codeAttr = binary.buildCodeAttributeInfo(8, 1, code.finish())
        val m = MethodInfo(
            Opcodes.ACC_PUBLIC,
            cp.utf8("<init>"),
            cp.utf8("()V"),
            listOf(AttributeInfo(codeUtf8, codeAttr))
        )
        return m to DebugMethod(
            "public ${gen.className}()",
            listOf("ALOAD_0", "INVOKESPECIAL $superInternalName.<init>()V", "RETURN")
        )
    }

    private fun buildConstructor(
        gen: GenClass,
        ctorSpec: Specialization,
        ctor: Constructor,
        cp: ConstantPool,
        codeUtf8: Int
    ): Pair<MethodInfo, DebugMethod> {

        val dbg = ArrayList<String>()
        val code = JvmCodeBuilder(cp, dbg)

        val superInternalName = getSuperInternalName(gen.scope)
        code.aload0()
        code.invokespecial(superInternalName, "<init>", "()V")

        // Array content init (mirrors JavaSourceGenerator.appendArrayContentInitialization())
        if (gen.scope == Types.Array.clazz &&
            ctor.valueParameters.size == 1 &&
            ctor.valueParameters[0].type == Types.Int
        ) {
            val elemType = resolveType(gen.specialization.typeParameters[0])
            code.aload0()
            code.iload(1)
            val refName = (elemType as? ClassType)?.let { internalNameOf(it) }
            code.newArray(elemType, refName)
            code.putfield(gen.internalName, "content", arrayDescriptorOf(elemType, gen.scope))
        }

        val body = ctor.body
        if (body != null) {
            val graph = ASTSimplifier.simplify(ctorSpec)
            graph.removeSuperCalls()
            prepareGraphForBytecode(graph)
            appendGraph(code, graph, gen, ctorSpec)
        }

        code.ret()

        val desc = ctorDescriptorOf(ctor, gen.scope)
        val codeAttr = binary.buildCodeAttributeInfo(64, code.maxLocals, code.finish())
        val m = MethodInfo(
            Opcodes.ACC_PUBLIC,
            cp.utf8("<init>"),
            cp.utf8(desc),
            listOf(AttributeInfo(codeUtf8, codeAttr))
        )

        val header = run {
            val b0 = builder.length
            try {
                appendConstructorHeader(gen.scope, gen.className, ctor, headerOnly = true)
                builder.substring(b0).trim()
            } finally {
                builder.setLength(b0)
            }
        }
        return m to DebugMethod(header, dbg)
    }

    private fun buildMethod(
        gen: GenClass,
        methodSpec: Specialization,
        method: Method,
        cp: ConstantPool,
        codeUtf8: Int
    ): Pair<MethodInfo, DebugMethod?> {

        val name = getMethodName(methodSpec)
        val desc = methodDescriptorOf(method, methodSpec, gen.scope)

        val nativeImpl = getNativeImplementation(method)
        val isAbstract = method.body == null && nativeImpl == null &&
                !isArrayGetter(methodSpec) && !isArraySetter(methodSpec)

        if (isAbstract || (gen.scope.scopeType == ScopeType.INTERFACE && method.body == null)) {
            return MethodInfo(
                Opcodes.ACC_PUBLIC or Opcodes.ACC_ABSTRACT,
                cp.utf8(name),
                cp.utf8(desc),
                emptyList()
            ) to null
        }

        val dbg = ArrayList<String>()
        val code = JvmCodeBuilder(cp, dbg)

        when {
            isArrayGetter(methodSpec) -> appendArrayGetter(code, gen, methodSpec)
            isArraySetter(methodSpec) -> appendArraySetter(code, gen, methodSpec)
            nativeImpl != null -> appendNativeMethod(code, gen.scope, method, nativeImpl)
            method.body != null -> {
                val graph = ASTSimplifier.simplify(methodSpec)
                prepareGraphForBytecode(graph)
                appendGraph(code, graph, gen, methodSpec)
                appendReturnIfMissing(code, method, methodSpec, gen.scope)
            }
            else -> appendReturnIfMissing(code, method, methodSpec, gen.scope)
        }

        val codeAttr = binary.buildCodeAttributeInfo(64, code.maxLocals, code.finish())
        val m = MethodInfo(
            Opcodes.ACC_PUBLIC,
            cp.utf8(name),
            cp.utf8(desc),
            listOf(AttributeInfo(codeUtf8, codeAttr))
        )

        val header = buildJavaMethodHeader(gen.scope, gen.className, methodSpec)
        return m to DebugMethod(header, dbg)
    }

    private fun buildJavaMethodHeader(classScope: Scope, className: String, methodSpec: Specialization): String {
        val b0 = builder.length
        try {
            appendMethodHeader(classScope, className, methodSpec, headerOnly = true)
            val header = builder.substring(b0).trim()
            return header
        } finally {
            builder.setLength(b0)
        }
    }

    private fun appendNativeMethod(code: JvmCodeBuilder, scope: Scope, method: Method, nativeImpl: String) {
        val impl = nativeImpl.trim().removeSuffix(";")
        if (impl == "System.out.println(arg0)") {
            code.getstatic("java/lang/System", "out", "Ljava/io/PrintStream;")
            code.iload(1)
            code.invokevirtual("java/io/PrintStream", "println", "(I)V")
            appendReturnIfMissing(code, method, noSpecialization, scope)
        } else {
            code.new0("java/lang/UnsupportedOperationException")
            code.dup()
            code.ldc("Unimplemented native: $impl")
            code.invokespecial("java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V")
            code.athrow()
        }
    }

    private fun appendArrayGetter(code: JvmCodeBuilder, gen: GenClass, methodSpec: Specialization) {
        val elemType = resolveType(gen.specialization.typeParameters[0])
        code.aload0()
        code.getfield(gen.internalName, "content", arrayDescriptorOf(elemType, gen.scope))
        code.iload(1)
        code.arrayLoad(elemType)
        code.returnByType(resolveType(methodSpec.method.resolveReturnType(methodSpec)))
    }

    private fun appendArraySetter(code: JvmCodeBuilder, gen: GenClass, methodSpec: Specialization) {
        val elemType = resolveType(gen.specialization.typeParameters[0])
        code.aload0()
        code.getfield(gen.internalName, "content", arrayDescriptorOf(elemType, gen.scope))
        code.iload(1)
        code.loadByType(2, elemType)
        code.arrayStore(elemType)
        // return Unit.__object__
        val unitInternal = internalNameOf(Types.Unit)
        code.getstatic(unitInternal, OBJECT_FIELD_NAME, "L$unitInternal;")
        code.areturn()
    }

    private fun prepareGraphForBytecode(graph: SimpleGraph) {
        // Unlike JavaSourceGenerator.prepareGraph(), we do NOT reconstruct to if/while nodes.
        // We also keep fields (no aggressive DCE) because stack optimization is out of scope for now.
        graph.giveLocalFieldsUniqueNames()
        graph.renumberFields()
    }

    private fun appendGraph(code: JvmCodeBuilder, graph: SimpleGraph, gen: GenClass, methodSpec: Specialization) {
        val locals = JvmLocals(this, code, graph)
        code.maxLocals = locals.maxLocals

        val labels = graph.blocks.associateWith { "B${it.blockId}" }
        for (block in graph.blocks) {
            code.label(labels.getValue(block))
            for (instr in block.instructions) {
                if (instr is me.anno.zauber.ast.simple.SimpleMerge) continue // handled by local coalescing
                appendInstr(code, locals, graph, instr)
                if (instr is SimpleReturn || instr is SimpleThrow) break
            }
            val last = block.instructions.lastOrNull()
            if (last is SimpleReturn || last is SimpleThrow) continue
            val cond = block.branchCondition
            if (cond != null && block.ifBranch != null && block.elseBranch != null && block.ifBranch != block.elseBranch) {
                locals.loadField(cond)
                code.ifne(labels.getValue(block.ifBranch!!))
                code.goTo(labels.getValue(block.elseBranch!!))
            } else {
                val next = block.nextBranch
                if (next != null) code.goTo(labels.getValue(next))
            }
        }
    }

    private fun appendInstr(
        code: JvmCodeBuilder,
        locals: JvmLocals,
        graph: SimpleGraph,
        instr: SimpleInstruction
    ) {
        when (instr) {
            is SimpleNumber -> {
                locals.pushConst(instr.dst, instr.base)
                locals.storeField(instr.dst)
            }
            is SimpleString -> {
                code.ldc(instr.base.value)
                locals.storeField(instr.dst)
            }
            is SimpleSpecialValue -> {
                when (instr.type) {
                    SpecialValue.NULL -> code.aconstNull()
                    SpecialValue.TRUE -> code.iconst(1)
                    SpecialValue.FALSE -> code.iconst(0)
                }
                locals.storeField(instr.dst)
            }
            is SimpleGetLocalField -> {
                locals.loadLocal(instr.field)
                locals.storeField(instr.dst)
            }
            is SimpleSetLocalField -> {
                locals.loadField(instr.value)
                locals.storeLocal(instr.field)
            }
            is SimpleGetObject -> {
                val objInternal = internalNameOf(instr.objectScope.typeWithArgs)
                code.getstatic(objInternal, OBJECT_FIELD_NAME, "L$objInternal;")
                locals.storeField(instr.dst)
            }
            is SimpleGetField -> {
                locals.loadField(instr.self)
                val ownerInternal = internalNameOf(instr.field.ownerScope.typeWithArgs.specialize(instr.specialization))
                val valueType = resolveType(instr.field.valueType ?: Types.NullableAny)
                code.getfield(ownerInternal, instr.field.newName, descriptorOf(valueType, instr.scope))
                locals.storeField(instr.dst)
            }
            is SimpleSetField -> {
                locals.loadField(instr.self)
                locals.loadField(instr.value)
                val ownerInternal = internalNameOf(instr.field.ownerScope.typeWithArgs.specialize(instr.specialization))
                val valueType = resolveType(instr.field.valueType ?: Types.NullableAny)
                code.putfield(ownerInternal, instr.field.newName, descriptorOf(valueType, instr.scope))
            }
            is SimpleAllocateInstance -> {
                val internal = internalNameOf(instr.allocatedType)
                code.new0(internal)
                code.dup()
                locals.storeField(instr.dst) // keep for ctor call
            }
            is SimpleConstructorCall -> {
                locals.loadField(instr.thisInstance)
                for (p in instr.valueParameters) locals.loadField(p)
                val ownerInternal =
                    internalNameOf((instr.method as Constructor).ownerScope.typeWithArgs.specialize(instr.specialization))
                val desc = ctorDescriptorOf(instr.method as Constructor, (instr.method as Constructor).ownerScope)
                code.invokespecial(ownerInternal, "<init>", desc)
            }
            is SimpleCall -> {
                val methodName = instr.methodName
                val thisType = resolveType(instr.thisInstance.type)
                val inline = (thisType in nativeNumbers) &&
                        instr.valueParameters.size == 1 &&
                        methodName in setOf("plus", "minus", "times", "div", "rem")
                if (inline) {
                    locals.loadField(instr.thisInstance)
                    locals.loadField(instr.valueParameters[0])
                    when (methodName) {
                        "plus" -> code.iadd()
                        "minus" -> code.isub()
                        "times" -> code.imul()
                        "div" -> code.idiv()
                        "rem" -> code.irem()
                    }
                    locals.storeField(instr.dst)
                } else {
                    locals.loadField(instr.thisInstance)
                    instr.selfInstance?.let { locals.loadField(it) }
                    for (p in instr.valueParameters) locals.loadField(p)
                    val target = instr.methods.values.first()
                    val ownerInternal = internalNameOf(target.ownerScope.typeWithArgs.specialize(instr.specialization))
                    val targetDesc = methodDescriptorOf(target, instr.specialization, target.ownerScope)
                    val isInterface = target.ownerScope.scopeType == ScopeType.INTERFACE
                    if (isInterface) {
                        code.invokeinterface(
                            ownerInternal,
                            getMethodName(instr.specialization),
                            targetDesc,
                            1 + instr.valueParameters.size
                        )
                    } else {
                        code.invokevirtual(ownerInternal, getMethodName(instr.specialization), targetDesc)
                    }
                    locals.storeField(instr.dst)
                }
            }
            is SimpleCompare -> {
                // only implement int compares for now
                locals.loadField(instr.left)
                locals.loadField(instr.right)
                val lTrue = code.newLabel("cmp_true")
                val lEnd = code.newLabel("cmp_end")
                val op = when (instr.type) {
                    CompareType.LESS -> Opcodes.IF_ICMPLT
                    CompareType.LESS_EQUALS -> Opcodes.IF_ICMPLE
                    CompareType.GREATER -> Opcodes.IF_ICMPGT
                    CompareType.GREATER_EQUALS -> Opcodes.IF_ICMPGE
                    // CompareType.EQUALS -> Opcodes.IF_ICMPEQ
                    // CompareType.NOT_EQUALS -> Opcodes.IF_ICMPNE
                }
                code.jump(op, lTrue)
                code.iconst(0)
                code.goTo(lEnd)
                code.label(lTrue)
                code.iconst(1)
                code.label(lEnd)
                locals.storeField(instr.dst)
            }
            is SimpleCheckIdentical -> {
                locals.loadField(instr.left)
                locals.loadField(instr.right)
                val lTrue = code.newLabel("id_true")
                val lEnd = code.newLabel("id_end")
                val op = if (instr.negated) Opcodes.IF_ACMPNE else Opcodes.IF_ACMPEQ
                code.jump(op, lTrue)
                code.iconst(0)
                code.goTo(lEnd)
                code.label(lTrue)
                code.iconst(1)
                code.label(lEnd)
                locals.storeField(instr.dst)
            }
            is SimpleCheckEquals -> {
                // Primitive int compare; otherwise Objects.equals
                val lt = resolveType(instr.left.type)
                val rt = resolveType(instr.right.type)
                val isPrim = lt in nativeTypes || rt in nativeTypes
                if (isPrim) {
                    locals.loadField(instr.left)
                    locals.loadField(instr.right)
                    // todo this seems very inefficient
                    val lTrue = code.newLabel("eq_true")
                    val lEnd = code.newLabel("eq_end")
                    val op = if (instr.negated) Opcodes.IF_ICMPNE else Opcodes.IF_ICMPEQ
                    code.jump(op, lTrue)
                    code.iconst(0)
                    code.goTo(lEnd)
                    code.label(lTrue)
                    code.iconst(1)
                    code.label(lEnd)
                } else {
                    locals.loadField(instr.left)
                    locals.loadField(instr.right)
                    code.invokestatic("java/util/Objects", "equals", "(Ljava/lang/Object;Ljava/lang/Object;)Z")
                    if (instr.negated) {
                        code.iconst(1)
                        code.ixor()
                    }
                }
                locals.storeField(instr.dst)
            }
            is SimpleInstanceOf -> {
                locals.loadField(instr.value)
                val internal = internalNameOf(resolveType(instr.type))
                code.instanceof0(internal)
                locals.storeField(instr.dst)
            }
            is SimpleReturn -> {
                if (graph.method is Constructor) {
                    code.ret()
                } else {
                    locals.loadField(instr.field)
                    code.returnByType(resolveType(instr.field.type))
                }
            }
            is SimpleThrow -> {
                locals.loadField(instr.field)
                code.athrow()
            }
            else -> {
                code.new0("java/lang/UnsupportedOperationException")
                code.dup()
                code.ldc("Unimplemented IR: ${instr.javaClass.simpleName}")
                code.invokespecial("java/lang/UnsupportedOperationException", "<init>", "(Ljava/lang/String;)V")
                code.athrow()
            }
        }
    }

    private fun appendReturnIfMissing(code: JvmCodeBuilder, method: MethodLike, spec: Specialization, scope: Scope) {
        val rt = resolveType(if (method is Constructor) Types.Unit else method.resolveReturnType(spec))
        if (rt == Types.Unit) {
            val unitInternal = internalNameOf(Types.Unit)
            code.getstatic(unitInternal, OBJECT_FIELD_NAME, "L$unitInternal;")
            code.areturn()
        } else {
            when (rt) {
                Types.Boolean, Types.Byte, Types.Short, Types.Char, Types.Int, Types.UInt -> {
                    code.iconst(0)
                    code.ireturn()
                }
                Types.Long, Types.ULong -> {
                    code.lconst0()
                    code.lreturn()
                }
                Types.Float, Types.Half -> {
                    code.fconst0()
                    code.freturn()
                }
                Types.Double -> {
                    code.dconst0()
                    code.dreturn()
                }
                else -> {
                    code.aconstNull()
                    code.areturn()
                }
            }
        }
    }

    private fun getSuperInternalName(scope: Scope): String {
        val superClass = scope.superCalls.firstOrNull { it.isClassCall }?.typeI ?: Types.Any
        val superType = resolveType(superClass)
        return when (superType) {
            Types.Any -> "java/lang/Object"
            is ClassType -> internalNameOf(superType)
            else -> "java/lang/Object"
        }
    }

    internal fun internalNameOf(type0: Type): String {
        val type = resolveType(type0)
        return when (type) {
            Types.Any -> "java/lang/Object"
            is UnionType -> internalNameOf(type.types.first { it != NullType })
            is ClassType -> internalNameOf(type)
            else -> "java/lang/Object"
        }
    }

    internal fun internalNameOf(type: ClassType): String {
        val spec = Specialization(type)
        val (name, packageScope) = getNameAndScope(type.clazz, spec)
        return (packageScope.path + name).joinToString("/")
    }

    private fun descriptorOf(type0: Type, scope: Scope): String {
        return when (val type = resolveType(type0)) {
            Types.Boolean -> "Z"
            Types.Byte, Types.UByte -> "B"
            Types.Short, Types.UShort -> "S"
            Types.Char -> "C"
            Types.Int, Types.UInt -> "I"
            Types.Long, Types.ULong -> "J"
            Types.Float, Types.Half -> "F"
            Types.Double -> "D"
            Types.Any -> "Ljava/lang/Object;"
            Types.Nothing -> "Ljava/lang/Object;"
            is UnionType -> descriptorOf(type.types.first { it != NullType }, scope)
            is ClassType -> "L${internalNameOf(type)};"
            else -> "Ljava/lang/Object;"
        }
    }

    private fun arrayDescriptorOf(elementType0: Type, scope: Scope): String {
        val elementType = resolveType(elementType0)
        return "[${descriptorOf(elementType, scope)}"
    }

    private fun ctorDescriptorOf(ctor: Constructor, scope: Scope): String {
        val sb = StringBuilder()
        sb.append('(')
        for (p in ctor.valueParameters) sb.append(descriptorOf(p.type.resolve(scope), scope))
        sb.append(")V")
        return sb.toString()
    }

    private fun methodDescriptorOf(method: MethodLike, spec: Specialization, scope: Scope): String {
        val sb = StringBuilder()
        sb.append('(')
        if (method.hasExplicitSelfType) sb.append(descriptorOf(method.selfType!!.specialize(spec), scope))
        for (p in method.valueParameters) sb.append(descriptorOf(p.type.specialize(spec), scope))
        sb.append(')')
        sb.append(if (method is Constructor) "V" else descriptorOf(method.resolveReturnType(spec), scope))
        return sb.toString()
    }

    private data class DebugMethod(val header: String, val body: List<String>)

    private fun buildDebugText(packageName: String, className: String, methods: List<DebugMethod>): String {
        val b = StringBuilder()
        if (packageName.isNotEmpty()) b.append("package ").append(packageName).append(";\n\n")
        b.append("public class ").append(className).append(" {\n")
        for (m in methods) {
            b.append("  ").append(m.header).append(" {\n")
            for (line in m.body) {
                b.append("    ").append(line).append('\n')
            }
            b.append("  }\n\n")
        }
        b.append("}\n")
        return b.toString()
    }
}
