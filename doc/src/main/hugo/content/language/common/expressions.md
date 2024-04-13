---
title: "Expressions"
type: "page"
weight: 35
draft: "false"
---

RIDDL allows expressions to be specified in various places. Most frequently
they are the operands to message constructors and `when` clauses in 
Gherkin examples.  This page shows you what you can do with expressions in 
RIDDL.

## Expressions
Expressions compute values of arbitrary types. Since RIDDL is not a 
programming language, the syntax used for expressions is minimal and 
abstract. There is no attempt in RIDDL to be computationally complete. 
Supporting expression is merely for the convenience to requirements writers.

## Conditional Expressions
Conditional expressions, or just conditionals, are expressions that can only
evaluate to a boolean value. These are used in places where a conditional 
value is expected, like in a `when` clause in a Gherkin example. While any 
expression value can be considered a conditional, there are several 
operators (`or`, `and`, `not` and the six comparison operators) that will 
only yield a conditional value.  Arithmetic operators are not considered 
conditional expression. Function call, field selection, and arbitrary 
expressions are considered conditionals.  

## LISP Style Prefix Syntax
RIDDL expressions uses prefix syntax. That is the name of each operator 
comes first (prefix) and its arguments follow within parentheses and comma 
separated. For example:
```riddl
sqrt(+(4,*(3,@MyState.numberField)))
```
This expression says multiply 3 by the value of "MyState.numberField" 
(presumably a numeric field in the state named "MyState"), add 4 to that, then 
take the square root of that sum. Note that "sqrt", "+" and "*" and "@" are 
all operators. In the case of @, no parentheses are required.


## Operators
In the subsections below we will discuss each of the operators that are 
defined by RIDDL.   

### Undefined Expression: ???
If you don't know what expression is needed, just use RIDDL's undefined 
operator, which is `???`. This can be considered a placeholder for future 
definition. 

### Arbitrary Expression: "expression"
RIDDL allows an arbitrary expression which is just a quoted string. When you
don't have time, inclination or the details of a computation, just describe 
it in text. For example:
```riddl
example Foo {
  when "conditions are right"
  then set State.Field to "the correct value"
}
```
In this example, two "arbitrary" expressions are used: 
* `conditions are right` presumably resolves to a conditional (boolean) value
* `"`the correct value` presumably resolves to a value compatible with the 
  type of the field `State.Field`

### Value Selection: @
The `@` operator selects the value of a named thing in the RIDDL definition. 
The `@` is followed immediately by a path identifier such as `Domain.Context.
Entity.State.Field`. The path identifier chosen must specify something that 
holds a value, for example the fields in a state definition or the message 
of an on clause (in a handler). 

### Arithmetic: +, -, / *, %, name
Arithmetic values perform computations. The usual five arithmetic operators, 
`+ - / * %` are permitted and they each take two arguments.  Additionally, 
any function name in all lower case, with 0 or more arguments can be used. 
The function name is not checked except that it must be in all lower case. 
For example, `sqrt(n)`, `log(n)`, `empty(@list)` are all valid expressions.

### Function Call: Path.To.Function(arg1=expression,...)
An expression may invoke a RIDDL defined function to obtain the expression's 
value. To make such an invocation, a path identifier is used to locate 
the function to be invoked and each of its arguments must be supplied 
between parentheses.  Arguments in a function call must be named, unlke in 
an arbitrary arithmetic operator. For example:
```riddl
function A { requires { i: Integer} yields { j: Integer }

A(i=3) 
```
This invokes function A with the required value 3 for "i" parameter

### Comparison Condition: <, <=, ==, !=, >=, >
The typical six comparison operators are supported. Each takes two operaonds 
only and compares them in different ways. The result is a conditional, true 
or false, depending on how the two values compare. The operators are:
* '<' - return true if operand 1 is less than operand 2
* '<=' - return true if operand 1 is less than or equal to operand 2
* '==' - return true if operand 1 is equal to operand 2
* '!=' - return true if operand 1 is not equal to operand 2
* '>=' - return true if operand 1 is greater than or equal to operand 2
* '>' - return true if operand 1 is greater than operand 2

## Logical: not, or, xor, and
Conditional expressions can be combined with the three logical operators:
* `not` - evaluates to the opposite of its operand, e.g. if the operand is 
  `true`then `not(true)` yields false. Requires exactly 1 operand.
* `or` - evaluates to `true` if any of its operands are `true`. Requires a 
  minimum of 2 operands
* `xor` - evaluates to `true` if only one of its operands are 
  `true` and the others are all `false`. Requires a minimum of 2 operands.
* `and` - evaluates to `true` if all of its operands are `true`. Requires a 
  minimum of 2 operands.

### Constants: True, False, Numbers
Constant values such as `true` and `false` (both conditionals as well), or 
any real, floating point, or integer number can be interpreted as 
expressions too.

### Ternary Expression: if(condition,then,else)
A computation may include the `if` operator with three operands. The first 
operand, `condition`, is a conditional (true/false) expression that determines 
whether `then`, an expression, is the result (condition ==`true` case) or 
`then`, also an expression, is the result (condition == `false` case). 

### Group Expression: (expression)
If if aids in clarity, you may place parentheses around an expression to 
group it together. With prefix operator notation, this isn't strictly needed 
but is provided for convenience. 
