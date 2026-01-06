package zauber

// this kind of is our ownership model, although,
//  we won't use these classes

value class Comptime(val data: NativePtr)

value class Owned(val owner: NativePtr, val classIndex: Int, val data: NativePtr)

value class Shared(val classIndex: Int, val data: NativePtr)

value class Value()