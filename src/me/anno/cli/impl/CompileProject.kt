package me.anno.cli.impl

import me.anno.zauber.ast.rich.member.Method
import java.io.File

class CompileProject(val root: File, val mainMethod: Method, val unitTests: List<Method>)