---
title: "Invariants"
type: "page"
draft: "false"
weight: 20
---
Invariants specify rules about the state of an entity that must never be
violated. Invariants typically come from business logic assertions. For example,
a business axiom of a warehouse might be that the supply of a product should
never be below 1. That is, the warehouse should never completely run out of 
a product it is storing. Reality, of course, must account for the supply running out; 
nevertheless specifying an invariant on the business intent may be important. This can be done 
in an entity with the `invariant` keyword:

```riddl
invariant InSupply is { ProductState.supply > 0 }
```
Invariants are checked every time the corresponding entity's state is modified. If an invariant 
fails to be satisfied, the state change is aborted and an error is generated. 

### Syntax
Specifying an invariant may use a variety of common conditional operators familiar to most 
programming languages and mathematics. The expression provided must evaluate to a boolean value, 
either true or false. A true value means the invariant is satisfied and a false value means the 
invariant is not satisfied.

#### Comparison Operators
* `=` _equality_ - The two operands must be equal: `op1 = op2`
* `!=` _inequality_ - The two operands must not be equal `op1 != op2`
* `<` _less_ - The first operand must be less than the second operand: `op1 < op2`
* `<=` _less-or-equal_ - The first operand must be less than or equal to the second operand: 
  `op1 <= op2`
* `>` _greater_ - The first operand must be greater than the second operaond: `op1 > op2`
* `>=` _greater-or-equal_ = The first operand must be greater than or equal to the second 
  operand: `op1 >= op2`

#### Logic and Grouping Operators
* `and` - _conjunction_ - Both operands must evaluate to true: `op1 and op2`
* `or` - _disjunction_ - Either operand must evaluate to true: `op1 or op2`
* `not` - _inverse_ - The inverse boolean value of the only operand: `not op1`
* `()` - _grouping_ - Parentheses are used to group operands into a single value: `( ... )`

#### Operand Types
* _constant_ - Constant values like numbers and strings may be used as operands
* _function_ - Function invocations that return the right type of value may be used to compare 
  runtime computed values.
* _state_ - State values of the entity can be used as operands; when multiple state objects are 
  specified, the name of the object must be used with the name of the field, separated by a period. 

