package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.simple.SimpleBlock
import me.anno.zauber.ast.simple.SimpleField

class SimpleBranch(val condition: SimpleField, val ifTrue: SimpleBlock, val ifFalse: SimpleBlock)