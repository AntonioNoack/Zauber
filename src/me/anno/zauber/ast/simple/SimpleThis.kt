package me.anno.zauber.ast.simple

import me.anno.zauber.scope.Scope

data class SimpleThis(val scope: Scope, val isExplicitSelf: Boolean)