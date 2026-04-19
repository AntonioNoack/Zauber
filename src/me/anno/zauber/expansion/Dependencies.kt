package me.anno.zauber.expansion

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType
import me.anno.zauber.types.specialization.MethodSpecialization
import me.anno.zauber.types.specialization.Specialization

/**
 * Given a set of entry points,
 *   find all methods and classes that are constructable,
 *   so we can create a minimal executable
 * */
object Dependencies {

    private class DependencyData {
        val createdClasses = HashSet<ClassType>()
        val calledMethods = HashSet<MethodSpecialization>()
    }

    private val reached by threadLocal { DependencyData() }

    fun addClass(type: ClassType) {
        // todo shall we register objects? when they are used only...
        if (reached.createdClasses.add(type)) {
            markSuperTypesConstructable(type)
            markChildMethodsReachable(type)
        }
    }

    // todo calling Int.toString() doesn't need Any.toString(), because super is not called
    // todo but calling Any.toString() and having Int constructable, now we need Int.toString()

    fun addMethod(method: MethodSpecialization) {
        // if method is a macro, skip it, we cannot execute it at runtime anyway
        if (method.method.flags.hasFlag(Flags.MACRO)) return

        if (reached.calledMethods.add(method)) {
            markMethodsInSubClassesReachable(method)
            markCalledMethodsReachable(method)
        }
    }

    private fun markMethodsInSubClassesReachable(method: MethodSpecialization) {
        // todo we must check all sub classes whether they are constructable,
        //  and if so, we must add their overridden method to be reachable
    }

    private fun markCalledMethodsReachable(method: MethodSpecialization) {
        // ASTSimplify method, and collect all called methods
        val simplified = ASTSimplifier.simplify(method)
        for (node in simplified.nodes) {
            for (instr in node.instructions) {
                when (instr) {
                    is SimpleCall -> addMethod(MethodSpecialization(instr.sample, instr.specialization))
                    is SimpleCheckEquals -> addMethod(MethodSpecialization(instr.method.resolved, instr.specialization))
                    is SimpleSelfConstructor -> addMethod(MethodSpecialization(instr.method, instr.specialization))
                    is SimpleAllocateInstance -> addClass(instr.allocatedType)
                    is SimpleString -> addClass(Types.String)
                    is SimpleNumber -> addClass(instr.dst.type as ClassType)
                    is SimpleGetObject -> addClass(instr.objectScope.typeWithArgs)
                    is SimpleGetTypeInstance -> addClass(instr.dst.type as ClassType)
                    // how do we handle dynamic macros? can only be inside macros, so we're fine (?)
                }
            }
        }
    }

    private fun markSuperTypesConstructable(type: ClassType) {
        // mark all super-types as constructable (because they are)
        val scope = type.clazz[ScopeInitType.AFTER_DISCOVERY]
        for (superType in scope.superCalls) {
            val superParams = ParameterList(type.clazz.typeParameters, type.typeParameters ?: emptyList())
            val superTypeI = superType.type.specialize(Specialization(superParams)) as ClassType
            addClass(superTypeI)
        }
    }

    private fun markChildMethodsReachable(type: ClassType) {
        val scope = type.clazz[ScopeInitType.AFTER_DISCOVERY]
        val methods = scope.methods0
        for (method in methods) {
            // todo if the super-method is reachable, mark this method reachable
        }
    }

    fun collectClassesAndMethods(): Pair<Set<ClassType>, Set<MethodSpecialization>> {
        val data = reached
        return data.createdClasses to data.calledMethods
    }

}