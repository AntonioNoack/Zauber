package zauber

fun <V> native(str: String): V = TODO(str) as V

class Native<SizeInBits: Int>

class NativeI1: Native<1>()
class NativeI8: Native<8>()
class NativeI16: Native<16>()
class NativeI32: Native<32>()
class NativeI64: Native<64>()

class NativeU1: Native<1>()
class NativeU8: Native<8>()
class NativeU16: Native<16>()
class NativeU32: Native<32>()
class NativeU64: Native<64>()

// actually depends...
class NativePtr: Native<64>()

class NativeF16: Native<16>()
class NativeF32: Native<32>()
class NativeF64: Native<64>()