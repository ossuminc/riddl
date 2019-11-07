# Compilation

The Riddl compiler performs several functions which are described in this 
section. There are several phases, as follows:

## Lexical Analysis
Riddl uses the excellent [fastparse](https://www.lihaoyi.com/fastparse/)
library by [Li Haoyi](http://www.lihaoyi.com/). This phase parses the raw
textual input to make sure it is syntactically correct. From that syntax, an
abstract syntax tree (AST) is produced. 

## Structural Analysis 
The Riddl AST is scanned to identified all the definitions (things with names),
create a symbol table from those names, and deduce the containment hierarchy
of the model defined by the AST. 
 
## Validity Analysis
The Riddl AST is very flexible. It can accept a wide range of input, even input
that doesn't make logical sense. For example, suppose you wrote this:
```text
entity MyLittlePachyderm {
  state: {
    thing: SomeType
 }
}
```
This defines an entity with a value, `thing`,  in its state of type 
`SomeType`.  The specification does not defined `SomeType` and it is not 
recognized as one of the pre-defined types.  Logically then, we don't know
the type of `thing` so our specification is incomplete. 

Validation is the process of finding all such omissions as well as:

* references to undefined things,
* references to existing things of the wrong type, 
* constructs that may be confusing,
* deviations from stylistic conventions
* definitional inconsistencies
* and, etc. 

The validation phase generates messages that identify the omissions and 
inconsistencies in the input specification. These validity breakages typically
stop the compiler from proceeding because using an invalid input model tends
to produce output that flawed and not useful.  

## Generation
A Riddl AST, having been successfully analyzed for structure and validity, is
ready to be translated into another form, which is the point of all this
bother in the first place. 
  
@@toc { depth=2 }

@@@ index

* []

@@@

