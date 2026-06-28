package me.anno.zauber.ast.rich.member

import me.anno.zauber.types.Type

class FieldDeclaration(val name: String, val type: Type?, origin: Long) : FieldDeclarationI(origin)