package me.anno.support.jvm

import me.anno.support.jvm.FirstJVMClassReader.Companion.API_LEVEL
import me.anno.support.jvm.FirstJVMClassReader.Companion.isStatic
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class SecondJVMClassReaderFull(
    val classScope: Scope,
    val methodScopes: Map<JVMMethodSignature, Scope>
) : ClassVisitor(API_LEVEL) {

    companion object {
        private val LOGGER = LogManager.getLogger(SecondJVMClassReaderFull::class)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        val methodScope = methodScopes[JVMMethodSignature(name, descriptor)] ?: return null
        val method = methodScope.selfAsMethod ?: methodScope.selfAsConstructor ?: return null

        LOGGER.debug("Translating method: $name, descriptor: $descriptor, signature: $signature, exceptions: ${exceptions?.toList()}, access: $access")
        return SecondJVMMethodReader(method, access.isStatic(), method.valueParameters)
    }
}