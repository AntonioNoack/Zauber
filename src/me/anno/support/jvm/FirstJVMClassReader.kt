package me.anno.support.jvm

import me.anno.generation.Specializations
import me.anno.libraries.Library
import me.anno.support.Language
import me.anno.support.jvm.expression.LazyJVMExpression
import me.anno.utils.CollectionUtils.getOrPutRecursive
import me.anno.utils.NumberUtils.toInt
import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.utils.RunOnceLazy
import me.anno.zauber.Zauber.root
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasAnyFlag
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.ast.rich.member.Constructor
import me.anno.zauber.ast.rich.member.Method
import me.anno.zauber.ast.rich.parameter.Parameter
import me.anno.zauber.ast.rich.parameter.ParameterMutability
import me.anno.zauber.ast.rich.parameter.ParameterType
import me.anno.zauber.ast.rich.parameter.SuperCall
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.io.IOException

class FirstJVMClassReader(val path: String, val classScope: Scope) : ClassVisitor(API_LEVEL) {

    companion object {

        const val API_LEVEL = ASM9
        private val LOGGER = LogManager.getLogger(FirstJVMClassReader::class)

        val jvmLibrary = Library().apply {
            language = Language.JAVA
            name = "JVM Class Reader"
        }

        val scanned by threadLocal {
            HashMap<String, Scope>()
        }

        fun splitPackage(name: String) = name.split('/', '$')

        private fun getScopeImpl(name: String, scopeType: ScopeType?): Scope {
            check(!name.endsWith(';')) { "Name must not end with ';'" }
            check(name[0] != '[') { "Name must not start with '['" }

            if (name == "java/lang/Object") {
                // must be overridden, otherwise
                // ArrayList<E>, E cannot be Int, because Int !is java.lang.Object
                return Types.Any.clazz
            }

            val parts = splitPackage(name)
            var scope = root
            for (pi in parts.indices) {
                val part = parts[pi]
                val type = if (scopeType != null &&
                    pi == parts.lastIndex &&
                    scope.getOrPut(part, null).scopeType == null
                ) scopeType else null
                scope = scope.getOrPut(part, type)
            }
            return scope
        }

        fun getScope(name: String, scopeType: ScopeType?): Scope {

            if (name.isEmpty()) return root

            val scope = getScopeImpl(name, scopeType)
            scanned.getOrPutRecursive(name, { scope }) { name, _ ->
                try {
                    scope.sourceLibrary = jvmLibrary
                    ClassReader(name)
                        .accept(FirstJVMClassReader(name, scope), 0)
                } catch (e: IOException) {
                    LOGGER.warn("Missing class $e")
                }
            }
            return scope
        }

        fun Int.isBridge() = hasFlag(ACC_BRIDGE)
        fun Int.isAnnotation() = hasFlag(ACC_ANNOTATION)
        fun Int.isEnum() = hasFlag(ACC_ENUM)
        fun Int.isInterface() = hasFlag(ACC_INTERFACE)
        fun Int.isMandated() = hasFlag(ACC_MANDATED)
        fun Int.isModule() = hasFlag(ACC_MODULE)
        fun Int.isNative() = hasFlag(ACC_NATIVE)
        fun Int.isOpen() = hasFlag(ACC_OPEN)
        fun Int.isPrivate() = hasFlag(ACC_PRIVATE)
        fun Int.isProtected() = hasFlag(ACC_PROTECTED)
        fun Int.isPublic() = hasFlag(ACC_PUBLIC)
        fun Int.isStatic() = hasFlag(ACC_STATIC)
        fun Int.isStaticPhase() = hasFlag(ACC_STATIC_PHASE)
        fun Int.isStrict() = hasFlag(ACC_STRICT)
        fun Int.isSuper() = hasFlag(ACC_SUPER)
        fun Int.isSynchronized() = hasFlag(ACC_SYNCHRONIZED)
        fun Int.isSynthetic() = hasFlag(ACC_SYNCHRONIZED)
        fun Int.isTransient() = hasFlag(ACC_TRANSIENT)
        fun Int.isTransitive() = hasFlag(ACC_TRANSITIVE)
        fun Int.isVarargs() = hasFlag(ACC_VARARGS)
        fun Int.isVolatile() = hasFlag(ACC_VOLATILE)

        fun Int.isFinal() = hasFlag(ACC_FINAL)
        fun Int.isAbstract() = hasFlag(ACC_ABSTRACT)

        // todo find out inner class by whether the parent package is a class
        fun getClassType(access: Int): ScopeType {
            return when {
                access.isInterface() -> ScopeType.INTERFACE
                access.isEnum() -> ScopeType.ENUM_CLASS
                else -> ScopeType.NORMAL_CLASS
            }
        }

        fun parseClassSignature(scope: Scope, signature: String?): Pair<List<Parameter>, List<ClassType>> {
            if (signature == null) return emptyList<Parameter>() to emptyList()
            val reader = SignatureReader(signature, scope)
            val generics = reader.readGenerics()
            val superClasses = ArrayList<ClassType>()
            while (reader.i < signature.length) {
                superClasses.add(reader.readType() as ClassType)
            }
            return generics to superClasses
        }

        fun parseMethodSignature(scope: Scope, signature: String, addToScope: Boolean): MethodSignature {
            val reader = SignatureReader(signature, scope)
            val typeParameters = reader.readGenerics()
            if (addToScope) scope.setTypeParams(typeParameters)

            reader.consume('(')
            val valueParameters = ArrayList<Parameter>()
            var i = 0
            while (signature[reader.i] != ')') {
                val type = reader.readType()
                valueParameters.add(
                    Parameter(
                        valueParameters.size, "arg${i++}",
                        ParameterType.VALUE_PARAMETER, ParameterMutability.DEFAULT,
                        type, scope, origin = -1
                    )
                )
            }
            reader.consume(')')

            val returnType = reader.readType()
            return MethodSignature(typeParameters, valueParameters, returnType)
        }
    }

    private val methodSignatures = HashMap<JVMMethodSignature, Scope>()

    fun descToType(desc: String): Type {
        return SignatureReader(desc, classScope).readType()
    }

    fun nameToType(desc: String): Type {
        // todo can be optimized
        return SignatureReader(desc, classScope).readClassType()
    }

    override fun visitEnd() {}

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        // val classScope = getScope(name, getClassType(access)).scope
        if (classScope.scopeType == null) {
            classScope.scopeType = getClassType(access)
        }

        classScope.addFlags(convertClassFlags(access))

        val (typeParameters, superTypesWithGenerics) = parseClassSignature(classScope, signature)
        classScope.setTypeParams(typeParameters)

        if (!classScope.isInterface() && classScope != Types.Any.clazz) {
            val superType = if (superName != null) {
                val superScope = getScope(superName, ScopeType.NORMAL_CLASS)
                val typeWithArgs = superTypesWithGenerics.firstOrNull { it.clazz == superScope }
                    ?: (if (superScope.hasTypeParameters) superScope.typeWithArgs else superScope.typeWithoutArgs)
                typeWithArgs
            } else Types.Any
            classScope.superCalls.add(SuperCall(superType, emptyList(), null, -1))
        }

        if (interfaces != null) {
            for (interfaceI in interfaces) {
                val superScope = getScope(interfaceI, ScopeType.INTERFACE)
                val typeWithArgs =
                    superTypesWithGenerics.firstOrNull { it.clazz == superScope }
                        ?: (if (superScope.hasTypeParameters) superScope.typeWithArgs else superScope.typeWithoutArgs)
                classScope.superCalls.add(SuperCall(typeWithArgs, null, null, -1))
            }
        }

        println("superCalls[$classScope]: ${classScope.superCalls}")
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        // outerName = className, innerName = subClassName, if not anonymous, else null
        // println("Visiting inner class $name, outerName: $outerName, innerName: $innerName, access: $access")
        val childType = when {
            access.isInterface() -> ScopeType.INTERFACE
            access.isEnum() -> ScopeType.ENUM_CLASS
            access.isStatic() -> ScopeType.NORMAL_CLASS
            else -> ScopeType.INNER_CLASS
        }
        getScope(name, childType)
    }

    fun convertVisibilityFlags(access: Int): Int {
        var r = 0
        r = r or access.isPublic().toInt(Flags.PUBLIC)
        r = r or access.isPrivate().toInt(Flags.PRIVATE)
        r = r or access.isProtected().toInt(Flags.PROTECTED)
        return r
    }

    fun convertClassFlags(access: Int): Int {
        var r = convertVisibilityFlags(access)
        r = r or access.isFinal().toInt(Flags.FINAL)
        r = r or (!access.isFinal()).toInt(Flags.OPEN)
        r = r or access.isAbstract().toInt(Flags.ABSTRACT)
        // todo strictfp-flag
        return r
    }

    fun convertMethodFlags(access: Int): Int {
        var r = convertVisibilityFlags(access)
        r = r or access.isFinal().toInt(Flags.FINAL)
        r = r or access.isNative().toInt(Flags.EXTERNAL)
        r = r or (!access.isFinal()).toInt(Flags.OPEN)
        r = r or access.isAbstract().toInt(Flags.ABSTRACT)
        // todo strictfp-flag
        return r
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {

        if (name == "getExactSizeIfKnown") {
            println("Visiting method: $path.$name, descriptor: $descriptor, signature: $signature, exceptions: ${exceptions?.toList()}, access: $access")
        }

        // todo should we lazy-read methods??? check performance...
        //  individually, or all at once?

        val origin = -1L
        val signature = signature ?: descriptor
        val classScope = if (access.isStatic()) classScope.getOrPutCompanion() else classScope
        val methodScope = classScope.generate(name, ScopeType.METHOD)
        val (typeParameters, valueParameters, returnType) = parseMethodSignature(methodScope, signature, true)

        val flags = convertMethodFlags(access)
        if (name == "<init>" || name == "<clinit>") {
            // clinit is not really a constructor, but we have nothing better at the moment
            methodScope.selfAsConstructor = Constructor(valueParameters, methodScope, null, null, flags, origin)
        } else {
            methodScope.selfAsMethod = Method(
                null, false, name, typeParameters, valueParameters, methodScope, returnType,
                emptyList(), null, flags, origin
            )
        }

        val s = JVMMethodSignature(name, descriptor)
        methodSignatures[s] = methodScope

        val hasBody = !access.hasAnyFlag(ACC_ABSTRACT or ACC_NATIVE)
        if (hasBody) {
            val lazy = RunOnceLazy {
                Specialization.noSpecialization.use {
                    val method = methodScope.selfAsMethod ?: methodScope.selfAsConstructor!!
                    ClassReader(path).accept(
                        SecondJVMClassReaderSingle(classScope, name, descriptor, method),
                        ClassReader.EXPAND_FRAMES // only needed when reading methods
                    )
                }
            }

            val asMethod = (methodScope.selfAsMethod ?: methodScope.selfAsConstructor)!!
            asMethod.body = LazyJVMExpression({
                lazy.value
                asMethod.body!!
            }, methodScope, origin)
        }

        return null
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor? {
        // todo visit fields and annotations...
        // println("Visiting field ${classScope.name}.$name, descriptor: $descriptor, signature: $signature, value: $value, access: $access")
        val valueType = descToType(signature ?: descriptor)
        val origin = -1L
        val initialValueForConst = when (value) {
            null -> null
            is Int -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Int }
            is Long -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Long }
            is Float -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Float }
            is Double -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Double }
            is String -> StringExpression(value, classScope, origin)
            else -> TODO("Get initial from ${value.javaClass.simpleName}")
        }

        val classScope = if (access.isStatic()) classScope.getOrPutCompanion() else classScope
        classScope.addField(
            null, false, true, null, name, valueType,
            initialValueForConst, Flags.NONE, origin
        )

        return null
        // return JVMFieldReader()
    }

}