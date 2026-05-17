package me.anno.zauber.expansion

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.ast.rich.Flags
import me.anno.zauber.ast.rich.Flags.hasFlag
import me.anno.zauber.ast.simple.ASTSimplifier
import me.anno.zauber.ast.simple.SimpleBlock.Companion.needsCopy
import me.anno.zauber.ast.simple.expression.*
import me.anno.zauber.scope.ScopeInitType
import me.anno.zauber.typeresolution.ParameterList.Companion.emptyParameterList
import me.anno.zauber.types.Specialization
import me.anno.zauber.types.Types
import me.anno.zauber.types.impl.ClassType

/**
 * Given a set of entry points,
 *   find all methods and classes that are constructable,
 *   so we can create a minimal executable
 *
 * todo we have some dependency conditions,
 *  but I still believe that we could define this as a graph coloring problem
 * */
object Dependencies {

    private val reached by threadLocal { DependencyData() }

    fun addClass(type: ClassType, markDefaultConstructor: Boolean = false) {
        addClass(Specialization(type), markDefaultConstructor)
    }

    fun addClass(type: Specialization, markDefaultConstructor: Boolean = false) {
        if (reached.createdClasses.add(type)) {
            markSuperTypesConstructable(type)
            markChildMethodsReachable(type)
        }

        if (markDefaultConstructor) {
            val ownerConstructor = type.clazz
                .getOrCreatePrimaryConstructorScope()
            addMethod(Specialization(ownerConstructor, emptyParameterList()))
        }
    }

    // todo calling Int.toString() doesn't need Any.toString(), because super is not called automatically
    // todo but calling Any.toString() and having Int constructable, now we need Int.toString(), because Any could be Int
    //  -> todo we could make casting one type to another be also data we collect, and then we can limit these interactions

    fun addMethod(method: Specialization) {
        check(method.isMethodLike())
        // if method is a macro, skip it, we cannot execute it at runtime anyway
        if (method.method.flags.hasFlag(Flags.MACRO)) return

        if (reached.calledMethods.add(method)) {
            markMethodObjectReachable(method)
            markMethodsInSubClassesReachable(method)
            markCalledMethodsReachable(method)
        }
    }

    private fun markMethodObjectReachable(method: Specialization) {
        val ownerClass = method.method.ownerScope
        if (ownerClass.isObjectLike()) {
            addClass(ownerClass.typeWithArgs, true)
        }
    }

    private fun markMethodsInSubClassesReachable(method: Specialization) {
        check(method.isMethodLike())
        // todo we must check all sub classes whether they are constructable,
        //  and if so, we must add their overridden method to be reachable
    }

    private fun markCalledMethodsReachable(method: Specialization) {
        // ASTSimplify method, and collect all called methods
        check(method.isMethodLike())
        if (method.method.isExternal() || method.method.isAbstract()) return

        val simplified = ASTSimplifier.simplify(method)
        for (node in simplified.blocks) {
            for (instr in node.instructions) {

                // if the dstType is a value class, we need the copy-instruction
                // todo this is not needed, if our target language supports copy OOTB, like C or C++

                // todo is this the right place to copy???
                //  I think we need to copy, when we really assign...
                //  e.g. when passing fields into a function...

                if (instr is SimpleAssignment) {
                    val dstType = instr.dst.type
                    if (dstType.needsCopy()) {
                        dstType as ClassType
                        val method = dstType.clazz.methods0
                            .firstOrNull { it.name == "copy" && it.valueParameters.isEmpty() }
                            ?: throw IllegalStateException("Missing .copy() in value-type $dstType")
                        val spec = Specialization(
                            method.scope, dstType.typeParameters
                                ?: emptyParameterList()
                        )
                        addMethod(spec)
                    }
                }

                when (instr) {
                    is SimpleCallable -> addMethod(instr.specialization)
                    is SimpleAllocateInstance -> addClass(instr.allocatedType)
                    is SimpleString -> addClass(Types.String, true)
                    is SimpleNumber -> addClass(instr.dst.type as ClassType, true)
                    is SimpleGetObject -> {
                        val scope = instr.objectScope[ScopeInitType.AFTER_DISCOVERY]
                        addClass(scope.typeWithArgs)
                        val constr = scope.primaryConstructorScope
                        if (constr != null) {
                            addMethod(Specialization.fromSimple(constr))
                        }
                    }
                    is SimpleGetTypeInstance -> addClass(instr.dst.type as ClassType)
                    is SimpleSetField -> reached.setFields.add(instr.specialization)
                    is SimpleGetField -> reached.getFields.add(instr.specialization)

                    // how do we handle dynamic macros? can only be inside macros, so we're fine (?)
                }
            }
        }
    }

    private fun markSuperTypesConstructable(type: Specialization) {
        // mark all super-types as constructable (because they are)
        val scope = type.clazz[ScopeInitType.AFTER_DISCOVERY]
        for (superType in scope.superCalls) {
            addClass(type.getSuperType(superType))
        }
    }

    private fun markChildMethodsReachable(type: Specialization) {
        val scope = type.clazz[ScopeInitType.AFTER_DISCOVERY]
        val methods = scope.methods0
        for (method in methods) {
            // todo if the super-method is reachable, mark this method reachable
        }
    }

    fun collectClassesAndMethods(): DependencyData {
        addClass(Types.Unit, true)
        return reached
    }

}