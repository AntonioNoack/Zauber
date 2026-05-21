package me.anno.zauber.ast.simple.fields

import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.types.Type

class LocalField(val field: Field?, var name: String, val type: Type, val id: Int)