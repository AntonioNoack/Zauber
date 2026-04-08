package me.anno.support.jvm

import me.anno.utils.CollectionUtils.getOrPutRecursive
import me.anno.zauber.Compile.root
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.rich.Parameter
import me.anno.zauber.ast.rich.SuperCall
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType
import me.anno.zauber.types.impl.ClassType
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes.*

class JVMBytecodeReader : ClassVisitor(API_LEVEL) {

    companion object {
        const val API_LEVEL = ASM9

        val scanned = HashMap<String, Scope>()

        fun splitPackage(name: String) = name.split('/')
        fun getScope(name: String, scopeType: ScopeType?): Scope {
            val parts = splitPackage(name)
            var scope = root
            for (pi in parts.indices) {
                val part = parts[pi]
                val type = if (pi == parts.lastIndex) scopeType else null
                scope = scope.getOrPut(part, type)
            }
            scanned.getOrPutRecursive(name, { scope }) { name, _ ->
                ClassReader(name)
                    .accept(JVMBytecodeReader(), 0)
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
    }

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        println("Visiting $name, access $access, version $version, signature: $signature, superName: $superName, interfaces: ${interfaces?.toList()}")

        val classScope = getScope(name, getClassType(access)).scope
        val (typeParameters, superTypesWithGenerics) = parseClassSignature(classScope, signature)
        classScope.typeParameters = typeParameters
        classScope.hasTypeParameters = true

        if (superName != null) {
            val superScope = getScope(superName, ScopeType.NORMAL_CLASS)
            val typeWithArgs = superTypesWithGenerics.firstOrNull { it.clazz == superScope } ?: superScope.typeWithArgs
            classScope.superCalls.add(SuperCall(typeWithArgs, emptyList(), null))
        }
        if (interfaces != null) {
            for (interfaceI in interfaces) {
                val superScope = getScope(interfaceI, ScopeType.INTERFACE)
                val typeWithArgs =
                    superTypesWithGenerics.firstOrNull { it.clazz == superScope } ?: superScope.typeWithArgs
                classScope.superCalls.add(SuperCall(typeWithArgs, null, null))
            }
        }
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        // outerName = className, innerName = subClassName, if not anonymous, else null
        println("Visiting inner class $name, outerName: $outerName, innerName: $innerName, access: $access")
    }

}