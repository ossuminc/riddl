# Task List For RIDDL

## RIDDL Language Improvements
* Incorporate definitions of fast data flow: pipes, streamlets (input, output, flow) and their 
  piping connections (consumers & producers).
* Support entity messaging within handlers to allow for aggregated root entities passing "tell" 
  operations to aggregated entities as actors
* Allow entities and contexts to define functions with Gherkin style specification
* Support arithmetic numeric computations as part of boolean/logic expressions where ever they 
  occur (e.g. invariants)
* ~~Allow multiple named state specifications in an entity to allow entities that are finite state 
  machines with FSM state transitions specified in handlers~~

## Implement Hugo Site Translator
* Translate RIDDL AST to Hugo site source as .md files
* Generate diagrams to include in the Hugo site to include: context maps, sequence diagrams, 
  entity diagrams & ERDs, data flow diagrams for pipes & messages
* Create a riddl-hugo-theme that provides all the shortcodes and YW styling for generated docs.
* Allow and validate riddl-hugo-theme shortcodes in the documentation of definitions.


