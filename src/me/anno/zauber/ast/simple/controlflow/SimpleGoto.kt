package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleBlock

class SimpleGoto(val condition: Field, val target: SimpleBlock)