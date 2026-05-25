package me.anno.zauber

import me.anno.utils.ResetThreadLocal.Companion.threadLocal
import me.anno.zauber.scope.Scope
import me.anno.zauber.scope.ScopeType

// todo make variable capture by lambdas explicit:
//  mark mutable fields as captured;
//  mutable fields then need some sort of wrapper in the method

// todo at compile-time define types???
// todo collect field names & visibility flags at collectNames-time? would allow for immediate name resolution for first names of chains
// todo make any field const-able; if a field is const:
//  - it must be computable from just that expression
//  - and other const values
//  - comptime exact maths?
//  - allow file IO?
//  - allow method calls
//  - execute with specializations ofc

// todo expand macros:
//   compile-time if
//   compile-time loop (duplicating instructions)
//   compile-time type replacements??? e.g. float -> double

// todo like Zig, just import .h/.hpp files, and use their types and functions

object Zauber {
    const val STDLIB_NAME = "zauber"
    val root by threadLocal {
        Scope("*").apply {
            // ensure zauber is a package
            getOrPut(STDLIB_NAME, ScopeType.PACKAGE).apply {
                setEmptyTypeParams()
            }
        }
    }
}