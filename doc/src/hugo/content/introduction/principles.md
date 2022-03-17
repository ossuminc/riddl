---
title: "Principles"
type: "page"
draft: "false"
weight: 40
---
RIDDL is a high level system specification language and as such must obey 
some principles of such languages. This page provides those principles.

## 1: Declarative
RIDDL is not an implementation language and does not pretend to be
computationally complete. RIDDL adopts this _what not how_ principle. 
Details are for software developers. The analyst or architect that writes
RIDDL documents wants to only specify what the system is while abstracting 
away the process of constructing it. It is like city planning, not the 
processes of laying pipes, providing power and paving roads. The end user 
ought to be as comfortable reading it as the developer. 

A specification is a statement of what needs to be produced, but not how 
it is to be realized (implemented). RIDDL specifications are aimed at 
modelling large, complicated knowledge domains. A RIDDL model must be 
complete enough that all the parts of it are recognizable and what it will 
do is discernable, but without understanding how it will be produced. 

Consequently, RIDDL is a declarative specification language. 

## 2: Both Data And Process
RIDDL models appreciate that the dichotomy between "doing" (process) and 
"being" (data) is false. Modern computing systems that model reality must 
be both in our view. Thus, strictly data-oriented specification languages 
nor strictly process-oriented specification languages will suffice. RIDDL 
must be both. While we are human _beings_ we must also be human _doings_; or 
as [Kurt Vonnegut published in "Deadeye Dick"](https://quoteinvestigator.com/2013/09/16/do-be-do/):
> Socrates: To be is to do
> 
> Sartre: To do is to be
> 
> Sinatra: Do be do be doooo.

## 3: Completeness  
The specification must provide the implementors all the information they need
to complete the implementation of the system, _and no more_. 

## 4: Sufficiently Formal 
A RIDDL specification should be sufficiently formal so that it can conceivably be
tested for consistency, correctness, completeness, and other desirable 
properties. The`riddlc` compiler for RIDDL input aims to achieve exactly 
this, automatically. 

## 5: Familiar Terms
The specification should discuss the system in terms that are normal and 
common for the users, implementors and subject-matter experts of the system. 
While RIDDL does introduce keywords that require some explanation (hence 
this documentation), one of the primary motivations for using DDD as a 
primary model for the language is to reinforce this principle. 

## 6: Rapidly Translatable
The RIDDL specification language exists to reduce the burden on system 
architects, business analysts, and others who must manage complexity and 
large volumes of concepts. Without the ability to rapidly translate the 
specification into other useful artifacts, the language would not have high 
utility. RIDDL can therefore be used to produce:
* Complete documentation websites for the model specified in RIDDL
* Various kinds of diagrams for better visual comprehension of the model
* Various kinds of code artifacts to ease the software developers burden
* Other artifacts through extension plugins  
