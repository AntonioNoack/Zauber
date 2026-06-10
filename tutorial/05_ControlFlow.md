## Control Flow

Control flow in Zauber is similar to Kotlin, but was extended by defer and errdefer from Zig.
Async-control flow is different from Kotlin.

### Branches

Zauber supports the standard if-else-if branch structure.
You must declare the condition in brackets, and an expression or block must follow.

```kotlin
if (x > 5) {
    println("A")
} else if (x < 7) println("C")
else return 15

val ifStatementsAreExpressions = if(x > 5) x else x+1
// or at least if their else-branch is defined
```

### Loops

For loops, Zauber supports while(), do-while() and for(var/val in) loops.
Again, the body must be an expression or a block.
Loops are not usable in assignments, because what would they even return?

```kotlin
while (x<5) x++

someLabel@ while(someConditionIsTrue) {
    executeSomeStuff()
    // break and continue are supported as well
    // you can give labels to repeating blocks to exit them early, or to enter the next iteration early
    break@someLabel
}

// do-while loops are supported, too,
do {
    someLogic()
    // and the condition operates in the scope of the body, so any field declared here can be used
} while(someCondition)


// for statements have a field part,
//  and a value part, which must be an Iterable
// fun iterator() is called on the value, and hasNext()+next() until hasNext() returns false
for(i in 0 until 100) {
    println("$i")
    if(i > 50) break
    // optional, because we're at the end anyway
    continue
}
```

### When Statements (Switch-Case)

Just like Kotlin, Zauber supports advanced switch-case logic in the following ways:

When statement with value:
```kotlin
when (value) {
    1, 2, 3 -> {} // list of values, separated by comma
    in listOf(1,2,3), in SetABC -> {} // container checks
    is Float -> {} // type checks
    4 if (value * value > 9) -> {} // with if-clause
    5, is Float, in ABC if (value + 1 % 3 == 0) -> {} // any combination, with additional if-clause 
    else -> {} // any other case
}
```

When statement from conditions:
```kotlin
when {
    // here, each branch must be some boolean condition
    value == 1 || value in listOf(1,2,3) -> {}
    else -> {}
}
```

If all cases are covered, or an else-branch is present, the result of each branch can be taken as a value.


### defer and errdefer

I really like the approach Zig takes for destructors to have them fully visible and directly next to the issuing code:
Not only will Zauber have destructors, but it will give the ability to put things at the end of the block.

defer will be executed in reverse order of the block after the statement was reached.
errdefer will be executed in reverse order if anything was thrown in/through that block after the statement was reached.

Both are meant as a way to clean up resources.

Using a destructor:
```kotlin
class Destructible {
    constructor() {
        println("Instance was created")
    }
    override fun finalize() {
        println("Instance was destroyed")
    }
}
```

Using defer:
```kotlin
fun something() {
    val d = Destructible()
    defer println("End of block reached")
}
```

and this method will print this:
```
Instance was created
End of block reached
Instance was destroyed
```

Using errdefer:
```kotlin
fun something(x: Int) {
    val d = Destructible()
    errdefer println("X was invalid")
    check(x > 0)
}
something(-1)
```
and this will crash, and print the following:
```kotlin
Instance was created
X was invalid
Instance was destroyed
```


### Async / Yield

Zauber has a custom idea about yielding.
Read more about that in [20_AsyncProgramming](./20_AsyncProgramming.md).