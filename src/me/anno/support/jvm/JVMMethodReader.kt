package me.anno.support.jvm

import me.anno.support.jvm.JVMClassReader.Companion.API_LEVEL
import me.anno.zauber.scope.Scope
import org.objectweb.asm.MethodVisitor

class JVMMethodReader(val methodScope: Scope) : MethodVisitor(API_LEVEL) {

}