
## Undefined Behavior

I'd like to avoid all undefined behavior

## Unsafe Number Operations
// WIP!!!

I like safe algorithms, so like Zig and Rust,
and mathematical operations are limited to their safe (without overflow) ranges,
and any violation will throw an error.

zauber.math.UnsafeInt/UnsafeLong will define types, where overflow is well-defined.
Maybe call them OverflowInt/OverflowLong?

I also like clamping float operations (forbidding Infinity and NaN), and disabling denormalized floats for performance,
but I wonder what the performance impact of the former is/how good hardware support is... .
Depending on the result, they will or will not be the default.
ClampedFloat?

