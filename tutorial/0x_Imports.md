
## Imports

To get access to classes outside the standard library and other packages, you must import them.
(Just like in Kotlin)

Import statements can be placed in any scope.
They should be at the top of each scope though, below the package statement.

```kotlin
import some.sample.library
import everything.from.*
import numpy as np

fun main() {
    library.use()
}
```

The star operator (*) can be used to import all children of a scope. Unless you need ten or more, it's better to
list the imports explicitly to not shadow any other fields or imports.

Kotlin doesn't have the 'as' syntax to rename imports, but I like that, so Zauber supports it.
