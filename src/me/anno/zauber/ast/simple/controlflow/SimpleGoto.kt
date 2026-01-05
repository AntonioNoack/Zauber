package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField

class SimpleGoto(val condition: SimpleField, val target: SimpleBlock)