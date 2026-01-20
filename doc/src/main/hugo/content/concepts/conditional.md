---
title: "Conditionals"
draft: false
---

Conditionals in RIDDL allow handlers to branch execution based on conditions.
RIDDL provides two conditional constructs: `when` for simple conditions and
`match` for multiple cases.

## When Statement

The `when` statement executes a block of statements when a condition is true.
Conditions can be literal strings or identifier references (from `let` bindings).
An optional `else` block handles the false case:

```riddl
handler MyHandler is {
  on command DoSomething {
    let authorized = "user has permission"
    when authorized then
      send event ActionCompleted to outlet Events
    else
      error "User not authorized"
    end
  }
}
```

### Condition Types

The `when` statement accepts three forms of conditions:

1. **Literal string**: A quoted condition description
   ```riddl
   when "user is authenticated" then
     // actions
   end
   ```

2. **Identifier reference**: Reference a condition bound with `let`
   ```riddl
   let valid = "input passes validation"
   when valid then
     // actions
   end
   ```

3. **Negated identifier**: Use `!` to negate an identifier
   ```riddl
   when !valid then
     error "Validation failed"
   end
   ```

### Using Else Blocks

The `else` block is optional and executes when the condition is false:

```riddl
let condition = "some boolean expression"
when condition then
  // executed when true
else
  // executed when false
end
```

### Alternative: Multiple When Statements

You can also use separate `when` statements with negation:

```riddl
let condition = "some boolean expression"
when condition then
  // executed when true
end
when !condition then
  // executed when false
end
```

Both approaches are valid. Use `else` when the two blocks are closely related,
and separate `when` statements when they represent distinct scenarios.

## Match Statement

The `match` statement handles multiple cases with pattern matching:

```riddl
handler ValidationHandler is {
  on command CreateUser {
    match "user validation" {
      case "email already exists" {
        error "Email address is already in use"
      }
      case "invalid email format" {
        error "Email format is invalid"
      }
      case "password too weak" {
        error "Password does not meet requirements"
      }
      default {
        send event UserCreated to outlet UserEvents
        morph entity User to state ActiveUser with record ActiveUserState
      }
    }
  }
}
```

### Match Structure

- `match "description" { ... }` - The description explains the scenario
- `case "condition" { ... }` - Handles a specific condition
- `default { ... }` - Handles the case when no other cases match

## Conditions

Conditions in RIDDL are expressed as quoted strings that describe the
boolean expression in natural language:

```riddl
"user is authenticated"
"order total exceeds threshold"
"item is in stock"
```

These conditions are implemented by the code generator or human developer.
RIDDL focuses on capturing the intent and flow, not the implementation details.

## Best Practices

1. **Use match for multiple error conditions**: When validating input with
   several possible error cases, use `match` with error cases and a `default`
   for the success path.

2. **Use when for simple branches**: For a single condition with two outcomes,
   use `let` + `when` + `when !`.

3. **Descriptive conditions**: Write conditions as clear, readable statements
   that document the business logic.

## Occurs In

* [Statements]({{< relref "statement.md" >}})
* [On Clauses]({{< relref "onclause.md" >}})

## Contains

* Condition strings
* Nested statements
