package me.anno.support.jvm

import me.anno.support.jvm.FirstJVMClassReader.Companion.API_LEVEL
import me.anno.support.jvm.FirstJVMClassReader.Companion.isStatic
import me.anno.zauber.ast.rich.MethodLike
import me.anno.zauber.logging.LogManager
import me.anno.zauber.scope.Scope
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor

class SecondJVMClassReaderSingle(
    val classScope: Scope,
    val methodName: String,
    val methodDescriptor: String,
    val method: MethodLike
) : ClassVisitor(API_LEVEL) {

    companion object {
        private val LOGGER = LogManager.getLogger(SecondJVMClassReaderSingle::class)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<String>?
    ): MethodVisitor? {
        if (name != methodName || descriptor != methodDescriptor) return null

        LOGGER.debug("Translating method: $name, descriptor: $descriptor, signature: $signature, exceptions: ${exceptions?.toList()}, access: $access")
        return SecondJVMMethodReader(method, access.isStatic(), method.valueParameters)
    }
}