package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleBlock

/**
 * while(condition) { body }
 * */
class SimpleLoop(val condition: Field, val body: SimpleBlock)