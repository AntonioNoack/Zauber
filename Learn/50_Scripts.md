# Zauber Scripts

Just like Python and Kotlin, it would be great to gradually extend a Zauber program for testing,
adding/replacing classes, modifying class properties, but keeping data in-tact as far as possible.

For this to work, I suggest we use the interpreter, and just compile any code annotated with
@Compile when performance is truly necessary.

Other libraries should be able to be used as-is, and because they cannot be modified, we should be able to compile them
(except for inline-functions).

