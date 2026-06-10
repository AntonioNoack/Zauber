
## Getters and Setters

Fields may have custom getter and setter functions, and they will be executed when you read/write that field,
but please keep them simple.

```
var ctr = 0
    get() {
        println("Accessing ctr, value: $field") // calling 'ctr' here would result in a stack overflow by recursion
        return field
    }
    set(value) {
        // field is the underlying field; it only is available in the getter and setter scopes
        field = value
        println("Set ctr to $ctr") // prints the state, but invokes the getter; use 'field' to avoid that.
    }
val someSampleField
    get() = ctr++ // automatically calls getter and setter
val someFieldWithPrivateSetter
    private set // can only be set from within this scope (class/package/block...)
```
