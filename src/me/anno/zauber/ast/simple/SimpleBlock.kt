package me.anno.zauber.ast.simple

import com.sun.media.sound.SimpleInstrument
import me.anno.zauber.ast.rich.Field

class SimpleBlock {
    val declaredFields = ArrayList<Field>()
    val instructions = ArrayList<SimpleInstrument>()
}