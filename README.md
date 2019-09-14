# ossum

## Introduction
Ossum is a tool for eliminating the boilerplate from reactive systems. 
It uses a DDD inspired specification language to allow developers to 
work at a higher level of specification (and detail) than they would if they
were coding directly in a programming language. It aims to relieve development
of the burden of maintaining infrastructural code through evolution of the
domain abstractions. 

## Language
doTERRA Commerce Sub-Domain
About This Directory
The files contained herein represent the Domain Driven Design (DDD) documents for a portion of the doTERRA domain: commerce. In order to interpret these documents correctly you should be familiar with DDD and have taken the Lightbend Reactive Architecture - Professional course. Some of the notation here is specific to doTERRA in good Agile fashion. This document explains some of that notation.

To get a 4 minute overview by the inventor of DDD, please see here: https://elearn.domainlanguage.com/ 
For more detail: Read DDD Distilled, or the longer original version: DDD
Some DDD Terminology You Need To Know
SME - Subject Matter Expert
A person who is an expert in their domain or subdomain. They define the terms and language for a specific knowledge area. 
Domain
any sphere of knowledge or activity. In this case our domain is all of doTERRA’s business operations.
Subdomain
A cohesive portion of a domain. Cohesion here means there is no contradictory language or conflation of terms and most of the interactions of things within the sub-domain is contained within the subdomain. That is, sub-domains exhibit low coupling (few interactions between subdomains) and high cohesion (significant interactions between contexts of the subdomain).   The ideas and concepts in a subdomain are clear and not clouded with details from other subdomains.
Bounded Context
A cohesive portion of a Sub-domain. Bounded contexts are typically organized around one kind of entity (a thing in the sub-domain that is persistent or has a well defined lifecycle). For example, an order is an entity and we would define a bounded context around that concept. Entities that qualify as the defining concept of a bounded context are typically aggregate root objects. We find them using a variety of techniques:
What are the objects of the subject-verb-object statements about the context?
Convert those statements into Commands, Queries, Events and Responses. What entities and value objects are implied from them?
Ask these questions about your entities:
Is it involved with all of the operations in the bounded context? 
Yes implies aggregate root
Does it aggregate other elements in the bounded context?
Yes implies aggregate root
Does deleting it cause the deletion of other elements in the bounded context?
Yes implies aggregate root
Does a single Command affect multiple instances? Note: Queries are allowed to touch multiple instances as they do not affect state.
No implies aggregate root


 
Ubiquitous Language
Every bounded context has a ubiquitous language. Even though some of the terms may be shared with other bounded contexts, the terms may have different meanings. Address to a shipping clerk means something very different than what it means to a network engineer. Words and concepts are only concretely understood within some bounded context. We call the terms defined for a bounded context the ubiquitous language. The ubiquitous language derives from the SME, the business expert, and not from the technical people providing software to the business. This distinction is critical to proper understanding of concepts, which is critical to correct implementation of software to support the business.
SVO - Subject-Verb-Object
These kinds of plain English statements about a domain or sub-domain are used to distinguish the boundaries of a bounded context. Typically the Object forms the key concept for a bounded context. 
The doTERRA Ontology

We use an ontology that is structured like this:
Domain (doTERRA)
Sub-Domain (they group related things, e.g. commerce, content, commissions, marketing …)
Applications (they allow users to interact with gateways)
Gateways (they provide access to doTERRA & shield external access from internals)
Bounded Context (they implement one thing really well using microservices)
Wrappers (a microservice that wraps/adapts interactions with one external system)

This directory provides all the Bounded Contexts, Wrappers and Gateways for the commerce sub-domain of doTERRA.
Templates
In this directory are templates to guide you through the creation of the documentation. The templates are:
doTERRA Gateway Definition Template
doTERRA Bounded Context Definition Template
doTERRA Wrapper Definition Template
Information Type Notation
Businesses move information around to get work done.  We believe that understanding the type of information is very important in overall understanding of what the information means and how the system is designed to work. Here is the notation used in the various documents. 

Value objects define types of data that entities and messages can use in their definitions. For the purposes of this documentation we want to keep it pretty abstract.  The basic notation is:
namesThatStartWithLower are value names for object parameters (i.e. not types)
boldFacedParameters are required. All others are assumed to be optional.
CapitalNamesInCamelCase are type names
TypeName: OtherTypeName is just a renaming of OtherTypeName as TypeName
FirstName: String means FirstName is the same as String. We do this for semantic clarity.
TypeName(valueName: TypeName, …) is a value object (not an entity).
{a, b, c} means a selection from a set of named identifiers (enumerations)
{ cat, dog, bird, ferret} just means pick one of those animal identifiers for the value.
{ AType, BType, CType} means a selection from a set of other types
{ TypeOne(abc), TypeTwo(def) } means either a TypeOne(abc) or a TypeTwo(def) value object
Type expressions followed by * means the cardinality is 0 or more (list)
e.g. MyType* means 0 or more of MyType
Type expressions followed by + means the cardinality is 1 or more (non-empty list)
E.g. MyType+ means 1 or more of MyType
Type expressions followed by ? means the cardinality is 0 or 1 (optional)
E.g. MyType? Means 0 or 1 instances of MyType
Type names in square brackets, means the cardinality is 0 or 1 (optional)
E.g. [OptionalType] means the value is either OptionalType or not provided (null, None)
Otherwise cardinality is exactly 1
(SKU → Price) is a type that represents a pair of values (one value name can hold both values)
(Type1, Type2, Type3, …) is a type that represents a tuple of distinct types
Operators can be combined to make complicated types.
e.g. (SKU → Price?)* means an array of pairs of SKU and optional Price. 
In Scala you would use Map[SKU,Option[Price]] as the type.
You can assume that some very common types are predefined:
UniqueId - a unique identifier for an entity (i.e. a UUID)
String - a string of characters 
Number - any number, real or integer
Boolean - a two state value: true or false (on or off).
Date - a month/day/year combinations
Timestamp - a precise instant in time (to the microsecond)
URL - A uniform resource locator as used in common web browsers, etc.
The following contains several examples of defining types and value objects with various kinds of types. 
SKU: String
SKU is another name for String
PriceList: Price*
PriceList type is 0 or more Price values
PhoneType: { mobilePhone, workPhone, homePhone }
PhoneType is an enumeration (choose one from a set) 
Price(retailPrice: Number, wholesalePrice: Number, employeePrice: Number, other: [Number])
Price is a simple value object the contains four values: three Number values pertaining to price and an optional “other” value of type Number. 
PriceMap(prices: (SKU → Price)*) 
PriceMap is an object type with a single value, prices, that is a mapping of SKU to Price (i.e. a sequence of SKU → Price pairs)
Business logic can be asserted for any named type. Simply document it under the definition. For example:
SKU: String
Must be a valid Stock Keeping Unit, alphanumeric characters only.


Defining Business Rules
Business rules are one of the most important parts of a bounded context. When you write business rules, try to use the terms of the Ubiquitous Language applicable to the bounded context. You find these terms from the subject matter expert for the bounded context you’re writing the rules for.  These terms should then be expressed in the Messages, Value Objects, Entities, Aggregates and Repositories defined for the bounded context. The terms to not derive from the technologists. 
To discover business rules, use these techniques:
Talk to business leaders about the details and interactions of the wrapper and the conversation will likely yield some business rules.
Look at the Commands (state change action) and Events (something happened) an ask yourself, “Under which conditions should this Command/Event  produce an error?”  The answer is likely a business rule.
Look at the Value Objects and identify combinations of field values that are not valid.
Specify key computations the bounded context must know how to do.
There are two ways to write business rules as defined in the following subsections.

The Gherkin Way
If the rule is amenable to it, use a “Gherkin” syntax. These statements are very formulaic, can be turned into test code very quickly by developers (using “Cucumber”), and are generally easy to understand by everyone. Try to express your business rule this way unless it just doesn’t fit the formula which is  like this:
Scenario: SCENARIO 
   Given STATE 
   When CONDITION
   Then RESULT
   Else ERROR (optional)

For example:
Scenario: User is greeted upon login
  Given the user "Aslak" has an account
  When he logs in
  Then he should see "Welcome, Aslak"
The English Way
If a rule just doesn’t fit the Gherkin pattern, then just write it in English. 
