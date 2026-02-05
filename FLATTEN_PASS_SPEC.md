# FlattenPass Specification

## Overview

`FlattenPass` is a RIDDL Pass that recursively removes `Include` and
`BASTImport` wrapper nodes from the AST, promoting their children to
the parent container. This produces a "flattened" AST where all
definitions are direct children of their logical parent, with no
intermediate file-organization wrappers.

## Motivation

The RIDDL AST uses `Include[CT]` and `BASTImport` nodes to represent
content loaded from external files (`.riddl` source files and `.bast`
pre-compiled files, respectively). These nodes are essential for:

- Source regeneration (pretty-printing back to the same file structure)
- Tracking provenance (which file a definition came from)
- BAST serialization fidelity

However, many consumers need "Include-transparent" access to the AST:

- **UI applications** (Synapify, RIDDL Playground) call accessor
  methods like `domain.contexts`, `context.entities` which only return
  direct children — content inside Include/BASTImport wrappers is
  invisible
- **Simulators and generators** that need a complete, flat model
  without parsing concerns
- **Analysis tools** that traverse the definition hierarchy

The RIDDL library provides some Include-aware accessors
(`getContexts`, `getEntities`, etc. in AST.scala) but they only cover
~7 types. There are 25+ accessor methods that don't traverse Includes.

`FlattenPass` provides a single transformation that makes ALL accessor
methods work correctly.

## Behavior

### Input
An AST (Root or any Container) containing Include and/or BASTImport
nodes at any nesting level.

### Output
The same AST with all Include and BASTImport nodes removed. Their
children are promoted to the parent container that held the
Include/BASTImport.

### Example

Before flattening:
```
Root
  Include (origin: "domains.riddl")
    Domain "Foo"
      Include (origin: "foo-contexts.riddl")
        Context "Bar"
        Context "Baz"
      Context "Qux"   (direct child)
    Domain "Quux"
  BASTImport (path: "precompiled.bast")
    Domain "Imported"
  Domain "Direct"     (direct child)
```

After flattening:
```
Root
  Domain "Foo"
    Context "Bar"
    Context "Baz"
    Context "Qux"
  Domain "Quux"
  Domain "Imported"
  Domain "Direct"
```

### Recursion

Flattening is recursive:
1. At each Container, Include/BASTImport nodes are replaced by their
   children
2. The process recurses into all child Containers
3. Nested includes (includes within includes) are fully resolved

### Node Types Handled

| Node Type | Contents Type | Action |
|-----------|--------------|--------|
| `Include[CT]` | `Contents[CT]` | Replace with `contents` children |
| `BASTImport` | `Contents[NebulaContents]` | Replace with `contents` children |

### Ordering

Children from Include/BASTImport nodes are inserted at the position
the wrapper occupied, preserving relative ordering.

## API Surface

### Standalone Utility (primary API)

```scala
object FlattenPass {
  /** Recursively flatten Include and BASTImport nodes in a Container.
    *
    * Modifies the Container's contents in-place. This is a one-way,
    * irreversible operation.
    *
    * Works on any Container: Root, Nebula, Domain, Context, etc.
    *
    * @param container The Container to flatten
    */
  def flatten(container: Container[?]): Unit
}
```

This is the primary entry point. It works on any `Container[?]`,
including `Root`, `Nebula`, `Domain`, `Context`, etc.

### Pass Integration

```scala
case class FlattenPass(input: PassInput, outputs: PassesOutput)
    extends Pass(input, outputs)
```

For use in the standard pass pipeline via `Pass.runThesePasses`.
The Pass calls `flatten(root)` in its `result()` method.

### RiddlLib JS API

```scala
@JSExport("flatten")
def flatten(root: js.Dynamic): js.Dynamic
```

Exposed via `RiddlAPI` for JavaScript/TypeScript consumers.

## Implementation Notes

### Contents Mutation

`Contents[CV]` is an opaque type over `mutable.ArrayBuffer[CV]`.
The implementation requires `clear()` extension method on Contents
(added as part of this work).

The flattening algorithm:
1. Collect all items, replacing Include/BASTImport with their children
2. Clear the container's contents
3. Add the flattened items back
4. Recurse into child Containers

### Type Safety

When flattening BASTImport nodes, their contents type is
`Contents[NebulaContents]` which may differ from the parent's content
type. Since the actual runtime values are the correct types (they were
originally parsed/imported to fit the parent context), an unchecked
cast is used. This is safe because Contents wraps a mutable
ArrayBuffer that doesn't enforce type parameters at runtime.

### Pass Framework Integration

The Pass extends the base `Pass` class with a no-op `process` method.
All work is done in `result()` to avoid modifying contents during
traversal (which would corrupt iteration).

### One-Way Transformation

Flattening is **irreversible**. Consumers that need the original
Include/BASTImport structure (e.g., for source regeneration or
pretty-printing) must retain the un-flattened AST or raw BAST bytes.

## Use Cases

### Synapify (Desktop Editor)
- Main process: Parse RIDDL -> BAST (preserves Includes/Imports)
- Renderer: Deserialize BAST -> Nebula -> `FlattenPass.flatten(nebula)`
  -> Nebula without wrappers -> Root for UI
- Raw BAST bytes stored for Generator pipeline

### RIDDL Playground (ossum.ai)
- Parse RIDDL in browser -> Root -> `FlattenPass.flatten(root)`
  -> flat Root for display

### Remote Generator (future)
- Receives raw BAST bytes WITH Includes/Imports preserved
- Does NOT flatten — needs file structure for source regeneration

## Prerequisites

### Contents.scala Changes

Add these extension methods to `Contents[CV]`:
```scala
inline def clear(): Unit = container.clear()
inline def remove(index: Int): CV = container.remove(index)
```

## Testing

Tests should verify:
1. Single Include at root level is flattened
2. Single BASTImport at root level is flattened
3. Nested includes (include within include) are fully resolved
4. Mixed Include and BASTImport nodes
5. Ordering is preserved after flattening
6. Direct children (non-Include/Import) are unaffected
7. Deep nesting: Domain > Include > Context > Include > Entity
8. Empty Include/BASTImport nodes (no children) are removed cleanly
9. `domain.contexts` returns all contexts after flattening
10. Works on Nebula (not just Root)
