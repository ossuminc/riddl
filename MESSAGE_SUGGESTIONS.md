# RIDDL Message → Suggestion Reference

This document maps every diagnostic message the **validation** and
**resolution** passes can generate to the remediation **suggestion**
(a.k.a. tip) attached to it. Placeholders such as `${name}` mirror the
interpolated values used in the actual code.

## How suggestions work

- Every `Messages.Message` carries an optional `suggestion: String` field.
- A suggestion is **retained and rendered** (as a `Suggestion:` line appended
  by `Message.format`) **only when `CommonOptions.provideTips` is enabled**
  (`riddlc --provide-tips …` or `riddlc advise …`). Otherwise the
  `Messages.Accumulator` strips it, so default output is unchanged.
- This replaces the former `AIHelperPass`. Any pass can attach a suggestion at
  the point it creates a message.

Rows are grouped by source file. The message column abbreviates very long
texts; the suggestion column is verbatim. (This file is a reference/AI-training
artifact, so table rows exceed the usual 80-column limit.)

---

## Resolution — `passes/.../resolve/ResolutionPass.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `Invalid result from findAnchorInSymTab(${topName}, …)` (Severe) | This is an internal RIDDL resolver error; please report it with the model that triggered it. |
| `Invalid result from findAnchorInParents(${topName}, …)` (Severe) | This is an internal RIDDL resolver error; please report it with the model that triggered it. |
| `PathId is empty; this should already be checked in resolveAPathId` (Severe) | This is an internal RIDDL resolver error; please report it with the model that triggered it. |
| `Path '${pathId}' resolved to ${foundDef}, in ${container}, but ${article(referTo)} was expected` | `'${pathId}' points at the wrong kind of definition. Point it at ${article(referTo)} instead, or rename the reference to match the intended ${referTo}.` |
| `Path '${pathId}' was not resolved … and it should refer to ${article(referTo)}` | `Define ${article(referTo)} named by '${pathId}', or correct the path so it names an existing ${referTo} reachable from this scope (try a fully-qualified path like 'Domain.Context.Name').` |
| `All alternates of \`${typeEx}\` must be ${kind} aggregates` | `Declare every alternative as ${article(kind)} aggregate, e.g. 'type X = ${kind} { ??? }'.` |
| `Type expression \`${typeEx}\` needs to be an aggregate for \`${kind}\`` | `Declare the referenced type as ${article(kind)} aggregate, e.g. 'type X = ${kind} { ??? }'.` |
| `Type expression \`${typEx}\` is not compatible with keyword \`${useCase}\`` | `Declare the type with the matching aggregate use case so it is compatible with \`${useCase}\`, e.g. 'type X = ${useCase} { ??? }'.` |
| `Type expression \`${typeEx}\` needs all elements to be a graph type for keyword \`graph\`` | Make every alternative a graph type, e.g. 'type X = graph of NodeType'. |
| `Type expression \`${typEx}\` needs to be a table for keyword \`table\`` | Declare the referenced type as a table, e.g. 'type X = table of RowType'. |
| `Path reference '${pid}' is ambiguous. Definitions are: …` | `Disambiguate '${pid}' with a more specific, fully-qualified path (e.g. 'Domain.Context.Entity.Name') so it matches exactly one definition.` |

## Resolution — `ReferenceMap.scala` / `UsageResolution.scala` / `SymbolsPass.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `Path Id '${pid}' found ${x} but a ${className} was expected` | `Point '${pid}' at a ${className}, or rename the reference to match the intended ${className}.` |
| `${defn} is unused` (Usage) | `Reference ${defn} from a handler, message flow, or another definition, or remove it if it is no longer needed.` |
| `${ty} is only referenced in path identifiers; …` (Completeness) | `Declare a field or state of type ${ty} so it can actually carry data, e.g. 'field someName: ${ty.id}'.` |
| `Non implicit value with empty name should not happen` (Severe) | This is an internal RIDDL symbol-table error; please report it with the model that triggered it. |

---

## `passes/.../validate/BasicValidation.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `An empty path cannot be resolved to ${article(T)}` | `Provide a non-empty path that names ${article(T)}, e.g. 'EnclosingScope.${T}Name'.` |
| `${ref} is empty` | `Name a message type here, e.g. '${kind} DoSomething'.` |
| `'${ref} should be one of these message types: ${kinds} but is ${article(mk)} type instead` | `Reference a type declared as one of: ${kinds}; or redeclare the target type with one of those aggregate use cases.` |
| `'${ref} should reference one of these types: ${kinds} but is a ${te} type instead` | `Point the reference at a type declared as one of: ${kinds} (e.g. 'type X = ${kind} { ??? }').` |
| `${ref} was expected to be one of these types; ${kinds}, but is ${article(kind)} instead` | `Reference a message type (${kinds}) rather than ${article(kind)}.` |
| `${defn} is overloaded with N distinct field types` (Warning) | Give the same-named fields a single consistent type, or rename them so each name maps to one type. |
| `${defn} is overloaded with N kinds` (Warning) | Rename one of the definitions so the same name does not refer to different kinds of definition. |
| `${defn} is overloaded with N distinct ${name} definitions` | `Rename or merge the duplicate ${name} definitions so each name is unique within its scope.` |
| `Identifiers must not be empty` | `Give this ${kind} a name of at least ${min} characters.` |
| `${kind} identifier '${id}' is too short. The minimum length is ${min}` (Style) | `Rename '${id}' to a more descriptive identifier of at least ${min} characters.` |
| `${name} in ${thing} should/must not be empty` (value) | `Provide a value for '${name}' in ${thing}, or remove the empty declaration.` |
| `${name} in ${thing} should/must not be empty` (list) | `Add at least one ${name} to ${thing}, or remove the empty declaration.` |
| `Path Identifier ${pid} … Cross-context references violate … bounded contexts …` (Warning) | `Add an Adaptor in ${containerContext} to translate messages from ${definitionContext}, or connect the contexts with a Streamlet (Source/Sink/Flow) instead of referencing across the context boundary directly.` |

---

## `passes/.../validate/DefinitionValidation.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `${definition} has duplicate content names` | `Rename or remove the duplicate definitions so each name is unique within ${definition}.` |
| `${authorRef} is not defined` | Define the referenced author (e.g. 'author Name is { name is "…" email is "…" }'), or correct the author reference to name an existing author. |
| `'${id}' evaded inclusion in symbol table!` (Severe) | This is an internal RIDDL error; please report it with the model that triggered it. |
| `${container} in ${parent} should have content` (Missing) | `Add at least one definition inside ${container} (or '???' as a placeholder), or remove it if it is not needed.` |
| `Metadata in ${identity} should not be empty` (Missing) | `Add metadata to ${identity}, such as 'briefly "…"', 'described as { … }', or 'by author …'.` |
| `In ${identity}, brief description … is too long. Max is 80 chars` (Warning) | Shorten the 'briefly' text to 80 characters or fewer; move any detail into a 'described as { … }' block. |
| `For ${identity}, description … is declared but empty` (Missing) | `Add description text to the 'described as' block for ${identity}, or remove the empty block.` |
| `For ${identity}, description … has an invalid URL: ${url}` | Use a valid absolute URL for the description link, e.g. 'https://example.com/docs'. |
| `${term}'s definition is too short. It must be at least 10 characters` (Warning) | `Expand the definition of ${term} to at least 10 characters so the glossary term is meaningful.` |
| `Option ${name}'s name is too short. It must be at least 3 characters` (Style) | Use an option name of at least 3 characters. |
| `${identity} should have a description` (Missing) | `Add documentation to ${identity}, e.g. 'briefly "A short summary"' or 'described as { | … | }'.` |
| `Option '${name}' in ${identity} is deprecated since ${ver}. Use '${replacement}' instead` (Style) | `Replace option '${name}' with '${replacement}'.` |
| `Option '${name}' in ${identity} expects ${expected} argument(s) but has ${argCount}` (Warning) | `Provide ${expected} argument(s) to option '${name}'.` |
| `Option '${name}' is not typically used on ${parentKind} definitions (expected: ${validParents})` (Style) | `Move option '${name}' to one of: ${validParents}, or remove it here.` |
| `Option '${name}' in ${identity} is not a recognized RIDDL option` (Style) | `Check the spelling of '${name}' against the recognized RIDDL options, or remove it if unintended.` |

---

## `passes/.../validate/TypeValidation.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `<regex PatternSyntaxException message>` | Correct the regular-expression syntax in this pattern (RIDDL uses Java regex syntax). |
| `Enumerator '${id}' must start with upper case` (Style) | `Start the enumerator name with an upper-case letter, e.g. '${id.capitalize}'.` |
| `Minimum value might be too small to store in a Long` (Warning) | Keep the minimum within the range of a 64-bit Long, or model the value with a different numeric type. |
| `Maximum value might be too large to store in a Long` (Warning) | Keep the maximum within the range of a 64-bit Long, or model the value with a different numeric type. |
| `Field names in aggregates should start with a lower case letter` (Style) | `Start the field name with a lower-case letter, e.g. '${lower(id)}'.` |
| `Field names in ${useCase} should start with a lower case letter` (Style) | `Start the field name with a lower-case letter, e.g. '${lower(id)}'.` |
| `Replica type expressions may not have cardinality` | Remove the cardinality from the replica's element type; a replica wraps a single replicable type. |
| `Type expression in Replica is not a replicable type` | Use a replicable element type for the replica: a mapping, sequence, set, or integer type. |
| `Minimum cardinality must be non-negative` | Use a minimum cardinality of 0 or greater. |
| `Maximum cardinality must be non-negative` | Use a maximum cardinality of 0 or greater. |
| `Minimum cardinality must be less than maximum cardinality` | Make the minimum cardinality strictly less than the maximum (e.g. {1..5}). |
| `The whole number part must be positive` | Specify a whole-number part of at least 1 for the Decimal, e.g. 'Decimal(10,2)'. |
| `The fractional part must be positive` | Specify a fractional part of at least 1 for the Decimal, e.g. 'Decimal(10,2)'. |

---

## `passes/.../validate/StreamingValidation.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `${streamlet} has no connections to any connector` (Completeness) | `Connect ${streamlet} to another streamlet with a connector, e.g. 'connector c is { from outlet ThisOutlet to inlet ThatInlet }'.` |
| `${source} is a source but has no downstream path to any sink` (Completeness) | Add connectors routing this source's outlet through to a sink so the data it produces is consumed. |
| `${sink} is a sink but has no upstream path from any source` (Completeness) | Add connectors routing a source's output into this sink so it receives data. |
| `The persistence option on ${connector} is not needed …` (Warning) | `Remove the 'persistent' option from ${connector}; both ends are in the same context.` |
| `The persistence option on ${connector} should be specified …` (Warning) | `Add the 'persistent' option to ${connector} since it spans a context boundary.` |
| `${portlet} is not connected` (Completeness) | `Connect ${portlet} with a connector, or remove it if it is unused.` |

---

## `passes/.../validate/ValidationPass.scala`

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `${handler} in ${parent} has no executable statements` (Completeness) | Add executable statements (tell, send, set, morph, become, reply) to the handler's on-clauses. |
| `${handler} … contains only prompt statements; …` (Completeness) | Add executable statements (tell, send, set, morph) alongside the 'prompt' statements so the handler does real work. |
| `${cmd} is a command with no fields; commands should carry data` (Missing) | `Add fields to ${cmd}, e.g. 'command X is { someField: Type }'.` |
| `${evt} is defined but no handler produces it` (Completeness) | `Send or tell ${evt} from a command handler so the event is produced, or remove the unused event.` |
| `${context} defines queries but no result types` (Completeness) | `Add a result type to ${context}, e.g. 'type XResult = result { ??? }'.` |
| `${context} defines results but no query types` (Completeness) | `Add a query type to ${context}, e.g. 'type XQuery = query { ??? }'.` |
| `${inv} is defined but not referenced by any 'require invariant' statement` (Usage) | `Reference ${inv} from a handler with 'require invariant ${inv.id}', or remove it if unused.` |
| `Empty 'on other' clause will silently discard unhandled messages` (Completeness) | Add statements to the 'on other' clause (e.g. log or error), or remove it if discarding is intentional. |
| `${onClause} should have statements` (Missing) | `Add one or more statements to ${onClause} (use '???' as a placeholder if needed).` |
| `Command processing in ${entity} should result in sending an event` (Completeness) | Send or tell an event from this command handler, e.g. 'send event SomethingHappened to outlet …'. |
| `Query processing in ${entity} should result in a reply or sending a result` (Completeness) | Reply with a result or send a result type from this query handler, e.g. 'reply result QueryResult'. |
| `Identifier '${id}' is too short` (let, Missing) | Use an identifier of at least 3 characters in the 'let' statement. |
| `Code statement body cannot be empty` (Missing) | Provide a non-empty code body, or remove the empty code statement. |
| `Field names should begin with a lower case letter` (Style) | `Start the field name with a lower-case letter, e.g. '${lower(id)}'.` |
| `Method names should begin with a lower case letter` (Style) | `Start the method name with a lower-case letter, e.g. '${lower(id)}'.` |
| `Method argument names should begin with a lower case letter` (Style) | `Start the argument name with a lower-case letter, e.g. '${lower(name)}'.` |
| `Type mismatch in ${connector}: …` | Make the inlet and outlet use the same type, or insert a Flow streamlet to transform between them. |
| `Unresolved PathId, ${pathId}, in ${outlet}` | `Define the type '${pathId}', or correct the outlet's type reference.` |
| `Unresolved PathId, ${pathId}, in ${inlet}` | `Define the type '${pathId}', or correct the inlet's type reference.` |
| `${type} should start with a capital letter` (Style) | `Capitalize the type name, e.g. '${typeName.capitalize}'.` |
| `${type} redefines built-in type '${typeName}'` | `Rename the type to something other than the built-in '${typeName}'.` |
| `${type} is a redundant case-variant of built-in type '${predef}'` (Style) | `Rename the type so it is not a case-variant of built-in '${predef}', or use the built-in '${predef}' directly.` |
| `${state} references an empty aggregate but must have at least one field` | `Add at least one field to the aggregate type used by ${state}, e.g. 'field someName: Type'.` |
| `${state} and ${typ} must not have the same name …` | `Rename either the state or the type so they do not share the name '${id}'.` |
| `${function} in ${parent} should have statements` (Missing) | `Add statements to the body of ${function} (use '???' as a placeholder if needed).` |
| `${handler} in ${entity} handles no commands or queries; …` (Warning) | `Add 'on command …' or 'on query …' clauses to ${handler}.` |
| `${handler} in ${repo} handles events; …` (Warning) | Move event handling to a projector; have the repository handle commands (writes) and queries (reads) instead. |
| `${handler} in ${proj} handles commands or queries; …` (Warning) | Have the projector handle events ('on event …') to build its read model instead of commands or queries. |
| `Include has no included content` | Ensure the included file exists and contains valid RIDDL content for this scope. |
| `Include has no source provided` | Provide a file path to include, e.g. 'include "entities.riddl"'. |
| `BAST load has no path specified` | Provide a .bast file path to import, e.g. 'import "model.bast"'. |
| `BAST load path '${path}' should end with .bast` (Warning) | Give the imported file a '.bast' extension. |
| `${schema} is ${kind} and should not define links` (Warning) | `Remove the links from this ${kind} schema, or change the schema kind to one that supports links.` |
| `${schema} is flat but defines N data nodes; …` (Warning) | Reduce the flat schema to a single data node, or change its kind to one that models multiple tables (e.g. relational). |
| `${schema} is a time-series schema but has no indices; …` (Warning) | Add an index on the time dimension of the time-series schema. |
| `${schema} is hierarchical … but has no links; …` (Warning) | Add links between data nodes to define the parent/child tree structure of the hierarchical schema. |
| `${schema} is a star schema … but has no links; …` (Warning) | Add links from the fact table to the dimension tables in the star schema. |
| `${schema} is graphical but has no links (edges)` (Warning) | Add links to define the edges connecting the nodes of the graphical schema. |
| `${schema} is relational … but has no links; …` (Warning) | Add links between data nodes to define foreign-key relationships in the relational schema. |
| `Link in ${schema} connects fields with incompatible types: …` | Make the two linked fields share the same type so the relationship is type-consistent. |
| `${schema} is a vector schema but defines N data nodes; …` (Warning) | Keep the vector schema to a single data node. |
| `${entity} must define at least one state` (Missing) | `Add a state to ${entity}, e.g. 'state ${id}State of ${id}Data is { ??? }'.` |
| `${entity} has only empty handlers` (Missing) | Add on-clauses to the entity's handlers, e.g. 'on command DoThing { ??? }'. |
| `${entity} is declared as an fsm, but doesn't have at least two states` | Define at least two states for the finite-state-machine entity (a state machine needs states to transition between). |
| `${entity} is declared as a finite-state-machine but its handlers contain no morph or become statements` (Completeness) | Add 'morph' or 'become' statements so the FSM transitions between its states. |
| `${state} in ${entity} has no handlers.` | `Add a handler to ${state} (or to ${entity}) to process messages in this state.` |
| `${entity} has no handlers and no states with handlers. …` | Add a handler to the entity, or add a state containing a handler, so the entity can process messages. |
| `${state} in ${entity} has no 'on init' clause …` (Completeness) | `Add an 'on init' clause to a handler of ${state} to initialize its fields.` |
| `${state} … has an 'on init' clause but no 'set' statement …` (Completeness) | Add 'set' statements in the 'on init' clause to initialize the state's fields. |
| `${entity} has no handlers to process messages` (Completeness) | Add a handler (on the entity or its state) to process incoming messages. |
| `${entity} has no 'on query' clause; …` (Completeness) | Add an 'on query' clause so the entity's state can be read. |
| `${entity} in ${context} has no outlet streamlet to publish events on` (Completeness) | `Add a Source or Flow streamlet with an outlet to ${context} so ${entity} can publish its events.` |
| `${entity} does not define an Id type for its identity` (Completeness) | `Define an Id type for ${entity} in its context, e.g. 'type Id = Id(${id})'.` |
| `${idType} is defined inside ${entity}; move it to the containing context …` (Completeness) | `Move ${idType} from ${entity} up to the containing context so other entities can reference it.` |
| `${idType} for ${entity} is defined outside the containing context; …` (Completeness) | `Move ${idType} into ${entity}'s context, and use adaptors for any inter-context references to it.` |
| `${entity} is event-sourced but this command handler does not emit an event` (Completeness) | Send or tell an event from this command handler so the event-sourced entity records its state change. |
| `${projector} lacks a required ${record} definition.` | `Add a record type to ${projector}, e.g. 'type ${id}Record = record { ??? }'.` |
| `${projector} must have exactly one Handler but has N` | Define exactly one handler for the projector. |
| `${projector} does not reference any repository to persist its projection` (Completeness) | `Reference a repository from ${projector}, e.g. 'updates repository SomeRepository'.` |
| `${projector} handler does not handle any events; …` (Warning) | Add 'on event …' clauses to the projector's handler to build its read model. |
| `${projector} does not persist its projection; …` (Completeness) | Add 'tell' statements in the projector's handler to write its read model to a repository. |
| `${projector} declares ${repoRef} but does not send it any messages` (Usage) | `Send messages to ${repoRef} with 'tell', or remove the unused repository reference.` |
| `${repository} should have at least one handler` (Missing) | `Add a handler to ${repository} to process commands (writes) and queries (reads).` |
| `${repository} handlers do not handle any commands or queries; …` (Warning) | Add 'on command …' (for mutations) and 'on query …' (for reads) clauses to the repository's handler. |
| `${adaptor} may not specify a target context that is the same as the containing ${c}` | `Point the adaptor at a different context than its containing ${c}.` |
| `${adaptor} should have at least one handler` (Missing) | `Add a handler to ${adaptor} to translate messages between the contexts.` |
| `${adaptor} has only empty handlers` (Missing) | Add on-clauses to the adaptor's handlers to translate messages between contexts. |
| `${adaptor} is ${direction} ${targetContext} but its handlers do not reference any message types …` (Warning) | `Reference message types from ${targetContext} in the adaptor's on-clauses.` |
| `Inbound ${adaptor} handles ${kind} '${pathId}' …, but inbound adaptors should handle events and results …` | Inbound adaptors should handle the target's output (events and results). Move command/query handling to an outbound adaptor. |
| `Outbound ${adaptor} handles ${kind} '${pathId}' …, but outbound adaptors should handle commands and queries …` | Outbound adaptors should handle the target's input (commands and queries). Move event/result handling to an inbound adaptor. |
| `Adaptor not contained within Context` | Define the adaptor inside a context. |
| `${streamlet} is a source but has N inlets; sources must have none` | Remove the inlets from the source; sources only produce data. |
| `${streamlet} is a source but has no outlets; …` | Add at least one outlet to the source so it can emit data. |
| `${streamlet} is a sink but has no inlets; …` | Add at least one inlet to the sink so it can receive data. |
| `${streamlet} is a sink but has N outlets; …` | Remove the outlets from the sink; sinks only consume data. |
| `${streamlet} is a flow but has no inlets; …` | Add at least one inlet to the flow. |
| `${streamlet} is a flow but has no outlets; …` | Add at least one outlet to the flow. |
| `${streamlet} is a merge but has N inlets; merges must have at least two` | Give the merge at least two inlets. |
| `${streamlet} is a merge but has no outlets; …` | Add at least one outlet to the merge. |
| `${streamlet} is a split but has no inlets; …` | Add at least one inlet to the split. |
| `${streamlet} is a split but has N outlets; splits must have at least two` | Give the split at least two outlets. |
| `${streamlet} is a router but has N inlets; routers must have at least two` | Give the router at least two inlets. |
| `${streamlet} is a router but has N outlets; routers must have at least two` | Give the router at least two outlets. |
| `${streamlet} should have a handler` (Missing) | `Add a handler to ${streamlet} to process streamed messages.` |
| `${streamlet} handlers do not send any messages to its outlets` (Completeness) | Add 'send' statements to the handler so the streamlet emits to its outlets. |
| `${streamlet} is a source but has no 'on init' or 'on other' clause …` (Completeness) | Add an 'on init' or 'on other' clause so the source generates data. |
| `Singly nested domains do not add value` (Style) | Merge the single nested domain into its parent, or add sibling domains to justify the nesting. |
| `Sagas must define at least 2 steps` | Define at least two saga steps so the saga coordinates a multi-step transaction. |
| `Saga step names must all be distinct` | Give each saga step a unique name. |
| `A saga step with do statements must also have revert statements, and vice versa` | Provide both 'do' and 'revert' statements for the saga step so its action can be compensated on failure. |
| `${step} do-step targets ${uncompensated} but the undo-step does not …` (Style) | `Add compensating revert statements targeting ${uncompensated} in the saga step's undo block.` |
| `${step} do-statements contain no 'tell command' to effect state changes` (Completeness) | Add a 'tell command' statement to the saga step's do-statements to effect a state change. |
| `${c} has entities but no Sink streamlet …` (Completeness) | `Add a Sink streamlet with an inlet to ${c} to receive and dispatch incoming messages.` |
| `${handler} in ${streamlet} handles messages but does not dispatch to any entity via 'tell'` (Completeness) | Add 'tell' statements so the streamlet handler dispatches incoming messages to an entity. |
| `${epic} is missing a user story` (Missing) | `Add a user story to ${epic}, e.g. 'by user SomeUser I want to … so that …'.` |
| `${user} is missing its role kind ('is a')` (Missing) | `Specify the user's role, e.g. '${id} is a "customer"'.` |
| `Sequential interactions should not be empty` (Missing) | Add interactions to the sequential block, or remove the empty block. |
| `Parallel interaction should not be empty` (Missing) | Add interactions to the parallel block, or remove the empty block. |
| `Optional interaction should not be empty` (Missing) | Add interactions to the optional block, or remove the empty block. |
| `Interactions must have a non-empty relationship` (Missing) | Describe the relationship for the interaction, e.g. '… "places" order'. |
| `${uc} doesn't define any interactions` (Missing) | `Add interactions to ${uc} describing the steps between users and the system.` |
| `${output} showing ${typRef} … is invalid because … can only send Events and Results` | Show an Event or Result here; vital definitions can only emit events and results. |
| `${input} sending ${putIn} … is invalid because … can only receive Commands and Queries` | Send a Command or Query here; vital definitions can only receive commands and queries. |
| `${c} has entities but no repository to persist them; entities are stateful and should be persisted` (Completeness) | `Add a repository to ${c}, e.g. 'repository ${c.id}Repository is { ??? }'.` |

The last row (context-with-entities-but-no-repository) is an **always-on**
completeness check (gated only by `showCompletenessWarnings`): a context that
has entities but no repository at all is incomplete because entities are
stateful and need durable storage. A placeholder `repository X is { ??? }`
counts as addressed.

---

## Advisory completeness checks (emitted only when `provideTips` is on)

These three checks were standalone proactive tips of the former
`AIHelperPass`. Because message types are often defined at *context* scope
(not inside the entity), they would false-positive on idiomatic models, so they
are advisory and fire only under `--provide-tips` / `riddlc advise`.

| Generated message (with placeholders) | Suggestion (with placeholders) |
|---|---|
| `${entity} defines no command types; …` (Completeness) | `Add a command type, e.g. 'type ${id}Command = command { ??? }'.` |
| `${entity} defines no event types; …` (Completeness) | `Add an event type, e.g. 'type ${id}Event = event { ??? }'.` |
| `Command ${cmd} in ${entity} is not handled by any on-clause` (Completeness) | `Add an on-clause for it, e.g. 'on command ${cmd.id} { ??? }'.` |
