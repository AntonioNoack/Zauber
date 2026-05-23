package me.anno.generation.jvm

sealed interface VerificationTypeInfo {
    fun write(w: JVMBytecodeWriter)

    class SimpleVerificationTypeInfo(val value: Int) : VerificationTypeInfo {
        override fun write(w: JVMBytecodeWriter) {
            w.u1(value)
        }
    }

    class ObjectVariable(val classIndex: Int) : VerificationTypeInfo {
        override fun write(w: JVMBytecodeWriter) {
            w.u1(7)
            w.u2(classIndex)
        }
    }

    companion object {
        val TopVariable = SimpleVerificationTypeInfo(0)
        val IntegerVariable = SimpleVerificationTypeInfo(1)
        val FloatVariable = SimpleVerificationTypeInfo(2)
        val DoubleVariable = SimpleVerificationTypeInfo(3)
        val LongVariable = SimpleVerificationTypeInfo(4)
        val NullVariable = SimpleVerificationTypeInfo(5)

        fun buildStackMapTable(frames: List<StackMapFrame>): ByteArray {
            val w = JVMBytecodeWriter()

            w.u2(frames.size)

            for (f in frames) {
                w.u1(255) // FULL_FRAME

                w.u2(f.offsetDelta)

                w.u2(f.locals.size)
                for (l in f.locals) {
                    l.write(w)
                }

                w.u2(f.stack.size)
                for (s in f.stack) {
                    s.write(w)
                }
            }

            return w.out.toByteArray()
        }

    }

}