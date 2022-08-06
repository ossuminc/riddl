---
title: "Compiling RIDDL"
date: 2022-02-25T10:35:37-07:00
draft: true
weight: 50
---

# Introduction
The Riddl compiler ([riddlc](../compilingriddl/riddlc/)) performs several functions. These functions are organized into phases of execution which we will summarize below. Each phase generates outputs that are used as inputs for later phases.

## Lexical Analysis
This phase parses the raw textual input of the RIDDL sources to make sure it is syntactically correct. As the RIDDL source is analyzed an in-memory model of the RIDDL file known as an abstract syntax tree (AST) is constructed. Incorrect syntax leads to errors without further analysis. RIDDL uses the excellent [fastparse](https://www.lihaoyi.com/fastparse/) library by [Li Haoyi](http://www.lihaoyi.com/) to perform this analysis. To speed up the process of further analysis and generation for specific kinds of outputs this in-memory model can be converted into a Binary Abstract Syntax Tree (BAST) file that can be persisted to disk. We will touch more on BAST outputs later.  

## Structural Analysis
The Riddl AST is then scanned to gather all the definitions (things with names) contained in the RIDDL source. Using these definitions a symbol table is created. Then finally, the containment hierarchy of the model defined by the AST is captured. Unless you are enhancing the RIDDL language you will likely not need to concern yourself with the symbol table. We present it here to help you understand the process of RIDDL file compilation.
 
## Validity Analysis
The Riddl AST is very flexible. It can accept a wide range of input, even input that doesn't make logical sense. For example, consider the following model:
```riddl
domain ReactiveBBQ is {
  context Kitchen is {
    entity Fridge is {
      state FullFridge is {
        food: VeganCheese  
      }
    }
  }
}
```
This little model should read pretty easily, but don't worry if you don't understand everything right now. This snippet is syntactically correct RIDDL source. However, if we tried to compile this source we will get an error during the validity analysis phase. Why? Inside the FullFridge state we define an attribute called food of type VeganCheese. In this model VeganCheese is not defined anywhere and it is also not recognized as a predefined type. Logically then, because we don't know the data type of the food attribute our specification is incomplete and the riddlc compiler will fail informing you of the error. 

Validation is the process of finding all such omissions as well as:

* references to undefined things,
* references to existing things of the wrong type, 
* constructs that may be confusing,
* deviations from stylistic conventions
* definitional inconsistencies
* and, etc. 

The validation phase generates messages that identify the omissions and inconsistencies in the input specification. These validity issues typically stop the compiler from proceeding because using an invalid input model tends to produce output that flawed and not useful. 

Maybe a last point here. The riddlc compiler will identify things that match the RIDDL grammar and are valid declarations. It cannot, however, identify things that make no sense to a human reading the specification. For example, if we were to declare an event within the model like so:

```riddl
...

type WaiterSeatedByCustomer is event { ??? }

...
```
No error would be generated. This is a syntaticaly valid RIDDL declaration, even though it is obvious to anybody reading the specification that the event is backwards. Customers are seated by Waiters. riddlc is not able to warn you of such inconcistencies.

# Translation
A RIDDL AST, having been successfully analyzed for structure and validity, is ready to be translated into another form, which is the point of all this bother in the first place.

RIDDL supports translation to many different forms of output. Some of these outputs that RIDDL supports out of the box were listed in the [RIDDL Ouptputs page](../riddloutputs/). But, it should also be noted that RIDDL translators are extensible to create nearly any output that you wish.
