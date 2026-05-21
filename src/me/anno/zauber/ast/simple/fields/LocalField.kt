package me.anno.zauber.ast.simple.fields

import me.anno.utils.StringStyles.GREEN
import me.anno.utils.StringStyles.LINK
import me.anno.utils.StringStyles.YELLOW
import me.anno.utils.StringStyles.style
import me.anno.zauber.ast.rich.member.Field
import me.anno.zauber.types.Type

// todo if it is val, we should just use simpleField
//  else, this is fine, becomes a mutable field, kind of

class LocalField(
    val field: Field?, var name: String, val type: Type, val id: Int,
    val isInsideMethod: Boolean
) {
    override fun toString(): String {
        return "${style("local#$id", YELLOW)}, ${style(name, GREEN)}: ${style(type.toString(), LINK)}"
    }
}