package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField

/**
 * while(condition) { body }
 * */
class SimpleLoop(val condition: SimpleField, val body: SimpleBlock)