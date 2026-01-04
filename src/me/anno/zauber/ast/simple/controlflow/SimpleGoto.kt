package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.rich.Field

class SimpleGoto(val condition: Field, val target: SimpleLabel)