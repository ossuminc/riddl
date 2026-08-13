# RIDDL JSON Input Method

`RiddlLib.parseJson(json, origin)` builds a RIDDL AST `Root` from a
structured JSON document, as an alternative to parsing RIDDL surface
text with `parseString`. It is **correct-by-construction**: the JSON
maps onto the typed AST, RIDDL's required type-expression arguments are
defaulted by the builder, and the result is then validated and/or
prettified by the existing machinery (there is no JSON-specific
validation path). References in the JSON are emitted as path identifiers
and resolved later by the standard passes.

This input method exists so that programmatic producers — particularly
AI models — can emit JSON (a format they handle reliably, and one that
supports schema-constrained decoding) and have RIDDL guarantee a
well-formed model. `root2RiddlSource` then renders guaranteed-valid
`.riddl` text.

The whole path is **Native-safe** (no I/O; upickle cross-compiled for
JVM/JS/Native). On JS it is exposed as `RiddlAPI.parseJson` (see
`riddlLib/js/types/index.d.ts`).

**Coverage:** this document describes the **Phase 1** subset. The JSON
schema grows additively each phase; `JSON_COVERAGE.md` tracks which AST
nodes are supported, planned, or deferred, and NOTEBOOK.md holds the
phased roadmap. Examples live in `riddlLib/json-examples/`.

## Pipeline

```
JSON string
  -> upickle.read[JsonModel.RootDto]   (parse + shape-check)
  -> JsonAstBuilder.build              (construct AST + apply defaults)
  -> RiddlResult[Root]
then (caller's choice, existing machinery):
  -> RiddlLib.validateRoot(root)       (standard passes)
  -> RiddlLib.root2RiddlSource(root)   (PrettifyPass -> valid RIDDL text)
```

Malformed JSON, an unknown `kind`, or a builder-level error (a missing
`Id` entity, an empty `Enum`/`Pattern`) yields a clean
`RiddlResult.Failure` — never a thrown exception. Undefined references
(to a type, message, entity, …) are **not** builder errors; they
surface as normal validation errors when the `Root` is validated.

## Top level

```jsonc
{ "domains": [ <domain>, ... ] }
```

Every named construct takes a `name` and an optional `brief` (a short
description string).

### domain

```jsonc
{
  "name": "Commerce",
  "brief": "online shopping",        // optional
  "authors":  [ <author>, ... ],     // optional
  "types":    [ <type>, ... ],       // optional
  "contexts": [ <context>, ... ]     // optional
}
```

### author

```jsonc
{
  "name": "reid",                     // the author's id
  "fullName": "Reid Spencer",
  "email": "reid@ossuminc.com",
  "organization": "Ossum Inc.",       // optional
  "title": "Architect"                // optional
}
```

### context

```jsonc
{
  "name": "Orders",
  "brief": "...",                     // optional
  "types":    [ <type>, ... ],        // optional
  "commands": [ <message>, ... ],     // optional
  "events":   [ <message>, ... ],     // optional
  "queries":  [ <message>, ... ],     // optional
  "results":  [ <message>, ... ],     // optional
  "entities": [ <entity>, ... ],      // optional
  "handlers": [ <handler>, ... ]      // optional
}
```

### type

```jsonc
{ "name": "OrderInfo", "brief": "...", "typeExpression": <typeExpression> }
```

### message (command / event / query / result)

A message is a `Type` whose expression is an aggregate tagged with the
appropriate use case; its `kind` is determined by which context array it
appears in.

```jsonc
{ "name": "PlaceOrder", "brief": "...", "fields": [ <field>, ... ] }
```

### entity

```jsonc
{
  "name": "Order",
  "brief": "...",                                   // optional
  "state": { "name": "current", "recordType": "OrderInfo" },  // optional
  "types":      [ <type>, ... ],                    // optional
  "handlers":   [ <handler>, ... ],                 // optional
  "invariants": [ <invariant>, ... ]                // optional
}
```

**State references a record by name** — RIDDL holds no fields directly
in a `state`; the fields belong to the named `record` type the state
references.

### handler / on-clause

```jsonc
{ "name": "Behavior", "onClauses": [ <onClause>, ... ] }
```

```jsonc
{
  "kind": "message" | "init" | "other" | "term",
  "message": { "ref": "PlaceOrder", "kind": "command" },  // for kind "message"
  "statements": [ "record the order details" ]            // Phase 1: prompt/`do` texts
}
```

`message.kind` is one of `command | event | query | result`. In Phase 1,
each statement string becomes a `prompt`/`do` statement.

### invariant

```jsonc
{ "name": "positive", "condition": "total > 0", "brief": "..." }
```

### field

```jsonc
{ "name": "sku", "brief": "...", "type": <typeExpression> }
```

## typeExpression

Tagged by `kind`, or wrapped with `cardinality`. Omitted arguments are
defaulted by the builder (see the table below).

```jsonc
{ "kind": "String", "min": 0, "max": 255 }   // both optional
{ "kind": "Id", "entity": "Order" }          // entity path REQUIRED
{ "kind": "Id", "entity": "Order", "keyword": "entity" }  // optional processor-kind keyword, as written
{ "kind": "UUID" }
{ "kind": "Boolean" }
{ "kind": "Date" }
{ "kind": "TimeStamp" }
{ "kind": "Integer" }                        // also Whole | Natural | Number | Real
{ "kind": "Decimal", "whole": 12, "fractional": 2 }
{ "kind": "Currency", "country": "USD" }
{ "kind": "Range", "min": 0, "max": 100 }
{ "kind": "Pattern", "pattern": ["^[a-z]+$"] }      // >= 1 required
{ "kind": "Enum", "values": ["Red", "Green"] }      // >= 1 required
{ "kind": "Alternation", "of": ["TypeA", "TypeB"] } // names of declared types
{ "kind": "Record", "fields": [ <field>, ... ] }    // a RIDDL `record`
{ "kind": "Alias", "ref": "SomeDeclaredType" }

// optional outer wrapper:
{ "cardinality": "optional" | "zeroOrMore" | "oneOrMore", "of": <typeExpression> }
```

### Defaults applied by the builder

| Omission | Result |
|---|---|
| `String` no `min`/`max` | `String(0, 255)` |
| `String` only `max` | `String(0, max)` |
| `String` only `min` | `String(min, 255)` |
| `Decimal` no/partial args | `Decimal(12, 2)` |
| `Range` no args | `range(0, 100)` |
| `Currency` no `country` | `Currency(USD)` |
| `Id` no `entity` | **error** (a path can't be defaulted) |
| `Id` no `keyword` | bare `Id(<path>)` (legal; the keyword is optional) |
| `Enum`/`Pattern` empty | **error** (need ≥ 1) |
| no `brief` | omitted (legal) |

## Phase 2 additions

More type expressions:

```jsonc
{ "kind": "UserId" } | { "kind": "Anything" } | { "kind": "Location" } | { "kind": "Nothing" }
// "Abstract" is the deprecated input spelling of "Anything"; output is always "Anything"
{ "kind": "Time" } | { "kind": "DateTime" } | { "kind": "Duration" }
{ "kind": "ZonedDate", "zone": "UTC" } | { "kind": "ZonedDateTime", "zone": "UTC" }   // zone optional
{ "kind": "Current" } | { "kind": "Length" } | { "kind": "Luminosity" }               // SI base units
{ "kind": "Mass" } | { "kind": "Mole" } | { "kind": "Temperature" }
{ "kind": "URI", "scheme": "https" }                  // scheme optional
{ "kind": "Blob", "blobKind": "JSON" }                // Text|XML|JSON|Image|Audio|Video|CSV|FileSystem; default Text
{ "kind": "Sequence", "of": <typeExpression> }
{ "kind": "Set", "of": <typeExpression> }
{ "kind": "Graph", "of": <typeExpression> }
{ "kind": "Replica", "of": <typeExpression> }
{ "kind": "Mapping", "from": <typeExpression>, "to": <typeExpression> }
{ "kind": "Table", "of": <typeExpression>, "dimensions": [2, 3] }
{ "kind": "EntityReference", "entity": "Order" }
{ "cardinality": "range", "of": <typeExpression>, "min": 1, "max": 5 }   // SpecificRange
```

Enumerators may carry explicit values; both forms may be combined:

```jsonc
{ "kind": "Enum", "values": ["Red", "Green"] }
{ "kind": "Enum", "enumerators": [ { "name": "Off", "value": 0 }, { "name": "On", "value": 1 } ] }
```

New definitions:

```jsonc
// domain:  "users": [ { "name": "Shopper", "isA": "a person who shops", "brief"?: "..." } ]
// context/entity: "constants": [ { "name": "MaxItems", "type": <typeExpression>, "value": "100", "brief"?: "..." } ]
```

## Phase 3 additions — statements and functions

Each statement is its own tagged object. A bare JSON string is shorthand for a
`prompt` statement (so `"statements": ["do X"]` from Phase 1 still works).
Statements appear in handler on-clauses and function bodies.

```jsonc
"text"                                                        // shorthand for prompt
{ "kind": "prompt", "text": "..." }
{ "kind": "error", "message": "..." }
{ "kind": "let", "name": "x", "type": "<typePath>", "expression": "..." }   // type optional
{ "kind": "code", "language": "scala", "body": "..." }
{ "kind": "require", "condition": "..." }                     // or "invariant": "<name>"
{ "kind": "require", "invariant": "UnderLimit", "argument": <value> }  // for `requires <type>` invariants
{ "kind": "set", "field": "<path>", "value": "..." }          // or "state": "<path>"
{ "kind": "send", "message": {"ref":"M","kind":"command"}, "to": "<path>", "portlet": "inlet" }  // inlet|outlet
{ "kind": "tell", "message": {"ref":"M","kind":"command"}, "to": "<path>", "processor": "entity" } // entity|context|projector|repository|adaptor
{ "kind": "morph", "entity": "<path>", "state": "<path>", "value": {"ref":"E","kind":"event"} }
{ "kind": "become", "entity": "<path>", "handler": "<path>" }
{ "kind": "yield", "message": {"ref":"R","kind":"result"} }              // also reads legacy "kind":"reply"
{ "kind": "when", "condition": "...", "negated": false, "then": [<stmt>], "else": [<stmt>] }   // or "conditionIdentifier"
{ "kind": "match", "expression": "...", "cases": [ { "pattern": "...", "statements": [<stmt>] } ], "default": [<stmt>] }
```

`message.kind` for statement refs accepts `command|event|query|result|record`.

Functions live in context/entity `functions`; `input`/`output` are field lists
(aggregations), `statements` is the body, `functions` nests:

```jsonc
{
  "name": "calc",
  "input":  [ { "name": "a", "type": { "kind": "Integer" } } ],
  "output": [ { "name": "r", "type": { "kind": "Integer" } } ],
  "statements": [ "compute r from a" ],
  "functions": [ { "name": "helper", "statements": [ "assist" ] } ]
}
```

`input`/`output` are the DEPRECATED bucketed form of a function's or saga's
`requires`/`returns`. Both clauses are ordinary contents in RIDDL, so a comment
may sit above, between or below them — and a field, having no position, cannot
say where. The canonical form is a `requires`/`returns` entry in the ordered
`contents` array, whose `arg` is a type-ref string (preferred) or a bare field
array (the deprecated inline aggregation):

```jsonc
{ "$kind": "function", "name": "calc", "contents": [
    { "$kind": "comment",  "text": "// what it needs" },
    { "$kind": "requires", "arg": "type Args" },
    { "$kind": "returns",  "arg": [ { "name": "r", "type": { "kind": "Integer" } } ] } ] }
```

`root2Json` writes both forms; a document that has ordered `contents` is read
from those alone, so the fields never double the clauses. A function or saga may
carry at most one of each — the RIDDL parser enforces it.

Records may carry `methods` alongside `fields`:

```jsonc
{ "kind": "Record",
  "fields":  [ { "name": "n", "type": { "kind": "Integer" } } ],
  "methods": [ { "name": "scaled", "type": { "kind": "Integer" },
                 "args": [ { "name": "by", "type": { "kind": "Integer" } } ] } ] }
```

## Ordered `contents` — the canonical shape

A container's children travel in ONE ordered array, each entry tagged with
`$kind`, in source order:

```jsonc
{ "contents": [
    { "$kind": "comment", "text": "// a header comment" },
    { "$kind": "domain",  "name": "Ordering", "contents": [ … ] } ] }
```

The tag key is `$kind`, not `kind`, because some entries carry a `kind` field of
their own (an on-clause, a schema). Tags are RIDDL keywords: `domain`, `context`,
`entity`, `type`, `command`, `event`, `query`, `result`, `record`, `state`,
`handler`, `onClause`, `function`, `adaptor`, `streamlet`, `projector`,
`repository`, `schema`, `connector`, `relationship`, `saga`, `step`, `epic`,
`case`, `interaction`, `group`, `containedGroup`, `input`, `output`, `author`,
`user`, `invariant`, `constant`, `comment`, `version`, `copyright`, `inlet`,
`outlet`, `field`, `method`, `term`, `requires`, `returns`.

A `with { … }` block works the same way, through `metadata.items`:

```jsonc
{ "metadata": { "items": [
    { "kind": "option", "name": "kind", "args": ["device"] },
    { "kind": "briefly", "value": "An order viewer" } ] } }
```

**Why an array and not per-kind fields.** RIDDL is fully reflective: a model
written to JSON and read back must recover the EXACT AST, and that includes the
ORDER of definitions within their parent. Per-kind arrays (`domains`, `types`,
`handlers`, …) cannot express order — reassembling them concatenates the groups
in a fixed sequence, so a comment written at the top of a file comes back at the
bottom.

An `include` and a BAST `import` are entries like any other, carrying their
already-loaded contents NESTED inside them — which is what keeps the builder
free of I/O and so usable on Native:

```jsonc
{ "$kind": "include", "origin": "file:///…/entities.riddl",
  "contents": [ { "$kind": "entity", "name": "Order", … } ] }
```

### Source locations

Every `contents` entry may carry `$at: [offset, endOffset]`, and the document says
once how to read those offsets:

```jsonc
{ "locations": { "origin": "orders.riddl", "basis": "origin" },
  "contents": [ { "$kind": "domain", "$at": [0, 412], "name": "Ordering", … } ] }
```

- **`basis: "origin"`** — the offsets index the file named by `origin`. This is
  what `root2Json` writes, because the model came from RIDDL and those are its
  real coordinates. Reading gives exact offsets and origin; line and column are
  not recoverable without that file, and resolving them is the caller's job.
- **`basis: "document"`** — the offsets index THIS JSON document. Use this when
  authoring JSON directly: the reader has the document, so line and column are
  exact and a diagnostic can quote the line you wrote.
- **absent** — no locations. Every node gets an empty location, which is what
  documents written before this do; they keep working unchanged.

Carrying locations matters for more than tidy messages. `Definition.equals`
includes the location, so with every location empty two same-named definitions
under different parents compare EQUAL — and collapse into one key in any map
keyed by a definition.

**The per-kind arrays still load**, so documents written against the older schema
keep working; they are DEPRECATED and will be removed in a later major.
`parseJson` accepts them silently; `parseJsonWithMessages` returns a
`Deprecation` naming the containers that used them. `root2Json` only ever writes
the ordered form.

### The aggregate flavour

RIDDL writes an aggregate body several ways, and they are not the same type
expression: `type X is { … }` is a bare aggregation, while `record X is { … }`,
`graph X is { … }` and `table X is { … }` are aggregates tagged with a use case.
The optional `aggregate` key on a `Record` says which one is meant:

```jsonc
{ "kind": "Record", "aggregate": "aggregation", "fields": [ … ] }  // type X is { … }
{ "kind": "Record", "aggregate": "record",      "fields": [ … ] }  // record X is { … }
{ "kind": "Record", "aggregate": "graph",       "fields": [ … ] }  // graph X is { … }
```

Accepted values are `aggregation` (a bare `{…}`, no keyword) and the RIDDL type
keywords `record`, `type`, `graph`, `table`, `command`, `event`, `query` and
`result`. **Omitting it means `record`**, which is what hand-authored JSON
usually wants — a `record` is what a `state … of record X` reference resolves
against. `root2Json` always writes the key explicitly, so a document it produced
round-trips to the flavour it started with.

## Phase 4 additions — streaming & integration

New context-level arrays: `adaptors`, `streamlets`, `projectors`,
`repositories`, `connectors`, `relationships`.

```jsonc
// adaptor
{ "name": "A", "direction": "inbound"|"outbound", "context": "<contextPath>",
  "types": [...], "constants": [...], "functions": [...], "handlers": [...] }

// streamlet (shape: source|sink|flow|merge|split|router|void)
{ "name": "S", "shape": "flow",
  "inlets":  [ { "name": "in",  "type": "<typePath>" } ],
  "outlets": [ { "name": "out", "type": "<typePath>" } ],
  "connectors": [ <connector> ], "types": [...], "handlers": [...] }

// connector (also valid at context level)
{ "name": "C", "from": "<outletPath>", "to": "<inletPath>" }

// projector
{ "name": "P", "repository": "<repositoryPath>", "handlers": [...], "types": [...] }

// repository + schema
{ "name": "Repo", "schema": {
    "name": "S", "kind": "Relational",          // RepositorySchemaKind name; default Other
    "data":    { "<field>": "<typePath>" },
    "links":   { "<name>": [ "<fieldA>", "<fieldB>" ] },
    "indices": [ "<field>" ] },
  "handlers": [...], "types": [...] }

// relationship (processor: entity|context|projector|repository|adaptor)
{ "name": "R", "withProcessor": "<path>", "processor": "projector",
  "cardinality": "1:1"|"1:N"|"N:1"|"N:N", "label": "..." }
```

## Phase 5 additions — sagas

`sagas` are valid at domain and context level.

```jsonc
{ "name": "Booking", "input": [ <field> ], "output": [ <field> ], "types": [...],
  "steps": [ { "name": "Reserve", "do": [ <stmt> ], "undo": [ <stmt> ] } ] }
```

## Phase 6 additions — modules & deep nesting

Top level may carry `modules` alongside `domains`; a module groups domains
(and authors). A domain may carry nested `domains` (subdomains).

```jsonc
{ "domains": [ { "name": "Outer", "domains": [ { "name": "Inner", ... } ], "contexts": [...] } ],
  "modules": [ { "name": "M", "authors": [...], "domains": [ ... ] } ] }
```

## Phase 7 additions — epics, use cases, interactions

`epics` are valid at domain level. An epic and each use case carry a user
story; interactions are tagged per kind.

```jsonc
{ "name": "Checkout",
  "userStory": { "user": "<userPath>", "capability": "...", "benefit": "..." },
  "shownBy": [ "https://..." ], "types": [...],
  "useCases": [ { "name": "Pay", "userStory": {...}, "interactions": [ <interaction> ] } ] }
```

Interactions (a generic ref is `{ "kind": "user"|"entity"|"context"|"group"|"output"|"input"|"adaptor"|"projector", "path": "..." }`):

```jsonc
{ "kind": "vague", "from": "...", "relationship": "...", "to": "..." }
{ "kind": "sendMessage", "from": <ref>, "message": {"ref":"M","kind":"command"}, "to": "<path>", "processor": "context" }
{ "kind": "arbitrary", "from": <ref>, "relationship": "...", "to": <ref> }
{ "kind": "self", "from": <ref>, "relationship": "..." }
{ "kind": "focusOnGroup", "user": "<path>", "group": "<path>" }
{ "kind": "directToURL", "user": "<path>", "url": "https://..." }
{ "kind": "showOutput", "output": "<path>", "relationship": "...", "user": "<path>" }
{ "kind": "selectInput", "user": "<path>", "input": "<path>" }
{ "kind": "takeInput", "user": "<path>", "input": "<path>" }
{ "kind": "sequential"|"parallel"|"optional", "interactions": [ <interaction> ] }
```

## Phase 8 additions — UI groups

`groups` are valid at context level. A group nests groups, contained groups,
inputs, and outputs.

```jsonc
{ "name": "Home", "alias": "page",          // alias default "group"
  "inputs":  [ { "name": "Login", "nounAlias": "form", "verbAlias": "takes", "takeIn": "<typePath>" } ],
  "outputs": [ { "name": "Greeting", "putOut": { "kind": "literal", "value": "hi" } },
               { "name": "Data", "putOut": { "kind": "type", "value": "<typePath>", "keyword": "record" } } ],
  "containedGroups": [ { "name": "Footer", "group": "<groupPath>" } ],
  "groups": [ { "name": "Sidebar", "alias": "pane", ... } ] }
```

`putOut.kind` is `type` (optional `keyword`, default "type"), `constant`, or
`literal`. Input `nounAlias` defaults to "input", `verbAlias` to "acquires".

## Phase 9 additions — rich metadata

Beyond the `brief` shorthand, the primary containers (domain, context,
entity, type) accept a `metadata` object:

```jsonc
"metadata": {
  "description": [ "line one", "line two" ],            // a block description
  "terms":   [ { "name": "SKU", "definition": [ "a stock keeping unit" ] } ],
  "options": [ { "name": "microservice", "args": [] } ],
  "byAuthors": [ "<authorPath>" ],                       // author references
  "attachments": [ { "name": "note", "mimeType": "text/plain", "value": "...", "inFile": false } ],
  "comments": [ "a line comment" ]
}
```

`attachments` with `inFile: true` become file attachments (value is a path);
otherwise string attachments. Coverage of the definition / type-expression /
statement / interaction surface is enforced by `JsonCoverageGuardTest`.

## 2.0 additions — full fidelity

The schema now tracks the AST's contents unions rather than the constructs
that happened to have fixtures, because tracking the latter is how it fell
behind in the first place.

**Children that were being dropped.** `RootDto` gained `authors`; `DomainDto`
gained `commands`/`events`/`queries`/`results`, `repositories` and
`connectors`; `EntityDto` gained `streamlets`, `connectors` and
`relationships`. Every processor DTO (adaptor, streamlet, projector,
repository, and context/entity) now carries the whole of `OccursInProcessor` —
`invariants`, `streamlets`, `connectors`, `relationships`, and for some also
`constants` and `functions`. A repository gained `schemas` (plural) beside the
back-compatible singular `schema`; `root2Json` writes only the plural, and a
reader accepts either.

**Metadata everywhere.** `metadata` used to appear on seven DTOs. Every
definition DTO carries it now, so a `described as` on a saga, a `term` on an
author or an `option` on a connector survives. `MetaDto` gained
`urlDescription` for `described at <url>`. An on-clause gained `brief`, which
it never had.

**Comments.** A container DTO carries `comments: [{text, inline?}]` for the
comments in its CONTENTS — distinct from `metadata.comments`, which are the
ones attached to the definition. `inline` marks a `/* … */` comment; its lines
are joined with newlines. Comments group with the container's other children,
so their position relative to neighbouring definitions is not preserved; the
schema groups every child by kind, so that ordering was already gone for
definitions. `RecordDto` and `MessageDto` carry them too, since
`AggregateContents` admits a comment between fields.

Groups, inputs and outputs do NOT carry `comments`: `OccursInGroup`,
`OccursInInput` and `OccursInOutput` admit no `Comment`. See `JSON_COVERAGE.md`
for the one comment position that is consequently not representable.

## Inverse — `root2Json`

`RiddlLib.root2Json(root, pretty)` (JS: `RiddlAPI.root2Json`) is the symmetric
inverse of `parseJson`: it serializes an `AST.Root` back to this JSON wire
schema. For any model in the supported subset,
`parseJson(root2Json(root))` re-validates identically, and `root2Json` is
stable under a second round-trip. It is **lossless for the documented subset**
and **best-effort (non-crashing) beyond it** — constructs the schema cannot
express (e.g. context-level invariants) are omitted rather than failing.
Implemented as a plain recursive serializer (`JsonSerializer`, the inverse of
`JsonAstBuilder`), Native-safe. Typical use: turn existing models into JSON,
e.g. `root2Json(bast2FlatAST(bytes))`.

## Example

```jsonc
{
  "domains": [
    {
      "name": "Commerce",
      "brief": "online shopping",
      "contexts": [
        {
          "name": "Orders",
          "types": [
            { "name": "OrderInfo", "typeExpression": { "kind": "Record", "fields": [
              { "name": "sku", "type": { "kind": "String" } },
              { "name": "quantity", "type": { "kind": "Integer" } } ] } }
          ],
          "commands": [
            { "name": "PlaceOrder", "brief": "place an order",
              "fields": [ { "name": "sku", "type": { "kind": "String", "max": 64 } } ] }
          ],
          "entities": [
            { "name": "Order",
              "state": { "name": "current", "recordType": "OrderInfo" },
              "handlers": [
                { "name": "Behavior", "onClauses": [
                  { "kind": "message", "message": { "ref": "PlaceOrder", "kind": "command" },
                    "statements": [ "record the order details" ] } ] } ] }
          ]
        }
      ]
    }
  ]
}
```

renders (via `root2RiddlSource`) to:

```riddl
domain Commerce is {
  context Orders is {
    record OrderInfo is { sku: String(0,255)  quantity: Integer }
    command PlaceOrder is { sku: String(0,64) } with { briefly "place an order" }
    entity Order is {
      state current of record OrderInfo
      handler Behavior is {
        on command PlaceOrder is { prompt "record the order details" }
      }
    }
  }
} with { briefly "online shopping" }
```
