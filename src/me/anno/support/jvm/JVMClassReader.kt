package me.anno.support.jvm

import me.anno.utils.CollectionUtils.getOrPutRecursive
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.*
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.expression.constants.NumberExpression
import me.anno.zauber.ast.rich.expression.constants.StringExpression
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.Type
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.*
import java.io.IOException

class JVMClassReader(val classScope: Scope) : ClassVisitor(API_LEVEL) {

    companion object {
        const val API_LEVEL = ASM9
        private val LOGGER = LogManager.getLogger(JVMClassReader::class)

        var ctr = 0
        val scanned = HashMap<String, Scope>()

        fun splitPackage(name: String) = name.split('/', '$')
        fun getScope(name: String, scopeType: ScopeType?): Scope {
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
            scanned.getOrPutRecursive(name, { scope }) { name, _ ->
                try {
                    ClassReader(name)
                        .accept(
                            JVMClassReader(scope),
                            ClassReader.EXPAND_FRAMES // only needed when reading methods
                        )
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

        fun parseMethodSignature(
            scope: Scope,
            signature: String,
            addToScope: Boolean
        ): MethodSignature {
            val reader = SignatureReader(signature, scope)
            val typeParameters = reader.readGenerics()
            if (addToScope) {
                scope.typeParameters = typeParameters
                scope.hasTypeParameters = true
            }

            reader.consume('(')
            val valueParameters = ArrayList<Parameter>()
            var i = 0
            while (signature[reader.i] != ')') {
                val type = reader.readType()
                valueParameters.add(Parameter(valueParameters.size, "arg${i++}", type, scope, origin = -1))
            }
            reader.consume(')')

            val returnType = reader.readType()
            return MethodSignature(typeParameters, valueParameters, returnType)
        }
    }

    fun descToType(desc: String): Type {
        return SignatureReader(desc, classScope).readType()
    }

    fun nameToType(desc: String): Type {
        // todo can be optimized
        return SignatureReader("L$desc;", classScope).readType()
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        val print = classScope.name == "ArrayList"
        if (print) println("Visiting $name, access $access, version $version, signature: $signature, superName: $superName, interfaces: ${interfaces?.toList()}")

        // val classScope = getScope(name, getClassType(access)).scope
        if (classScope.scopeType == null) {
            classScope.scopeType = getClassType(access)
        }

        val (typeParameters, superTypesWithGenerics) = parseClassSignature(classScope, signature)
        classScope.typeParameters = typeParameters
        classScope.hasTypeParameters = true

        if (superName != null) {
            val superScope = getScope(superName, ScopeType.NORMAL_CLASS)
            val typeWithArgs = superTypesWithGenerics.firstOrNull { it.clazz == superScope }
                ?: (if (superScope.hasTypeParameters) superScope.typeWithArgs else superScope.typeWithoutArgs)
            classScope.superCalls.add(SuperCall(typeWithArgs, emptyList(), null))
        }

        if (interfaces != null) {
            for (interfaceI in interfaces) {
                val superScope = getScope(interfaceI, ScopeType.INTERFACE)
                val typeWithArgs =
                    superTypesWithGenerics.firstOrNull { it.clazz == superScope }
                        ?: (if (superScope.hasTypeParameters) superScope.typeWithArgs else superScope.typeWithoutArgs)
                classScope.superCalls.add(SuperCall(typeWithArgs, null, null))
            }
        }
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

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        if (classScope.name != "ArrayList" || (name != "clear" && name != "add")) return null

        println("Visiting method: $name, descriptor: $descriptor, signature: $signature, exceptions: ${exceptions?.toList()}, access: $access")
        // todo should we lazy-read methods??? check performance...
        //  individually, or all at once?

        val origin = -1
        val signature = signature ?: descriptor
        val methodScope = classScope.generate(name, ScopeType.METHOD)
        val (typeParameters, valueParameters, returnType) = parseMethodSignature(methodScope, signature, true)

        val method = if (name == "<init>" || name == "<clinit>") {
            // clinit is not really a constructor, but we have nothing better at the moment
            Constructor(valueParameters, methodScope, null, null, Flags.NONE, origin)
        } else {
            Method(
                null, false, name, typeParameters, valueParameters, methodScope, returnType,
                emptyList(), null, Flags.NONE, origin
            )
        }

        return JVMMethodReader(method, access.isStatic(), valueParameters)
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
        val valueType = nameToType(signature ?: descriptor)
        val origin = -1
        val initialValueForConst = when (value) {
            null -> null
            is Int -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Int }
            is Long -> NumberExpression("${value}l", classScope, origin).apply { resolvedType = Types.Long }
            is Float -> NumberExpression("${value}f", classScope, origin).apply { resolvedType = Types.Float }
            is Double -> NumberExpression("$value", classScope, origin).apply { resolvedType = Types.Double }
            is String -> StringExpression(value, classScope, origin)
            else -> TODO("Get initial from ${value.javaClass.simpleName}")
        }

        classScope.addField(
            null, false, true, null, name, valueType,
            initialValueForConst, Flags.NONE, origin
        )

        return null
        // return JVMFieldReader()
    }

}