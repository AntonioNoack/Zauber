package me.anno.zauber.ast.simple.controlflow

import me.anno.zauber.ast.rich.Field
import me.anno.zauber.ast.simple.SimpleBlock

class SimpleBranch(val condition: Field, val ifTrue: SimpleBlock, val ifFalse: SimpleBlock)