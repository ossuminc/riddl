/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.Root
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/** Incremental BAST round-trip tests to isolate serialization issues */
class BASTIncrementalTest extends AnyWordSpec with Matchers {

  /** Helper to test BAST round-trip */
  def testRoundTrip(name: String, riddlText: String): Unit = {
    val input = RiddlParserInput(riddlText, name)

    TopLevelParser.parseInput(input, true) match {
      case Right(root: Root) =>
        val passInput = PassInput(root)
        val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
        val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
        val bastBytes = output.bytes

        BASTReader.read(bastBytes) match {
          case Right(nebula) =>
            // Success - verify we got something back
            nebula.contents.toSeq.size should be > 0
          case Left(errors) =>
            fail(s"BAST read failed for '$name':\n${errors.format}")
        }

      case Left(messages) =>
        fail(s"Parse failed for '$name': ${messages.format}")
    }
  }

  "BAST Incremental Tests" should {

    // Level 1: Simplest structures
    "handle empty domain" in {
      testRoundTrip("empty-domain", """domain Empty is { ??? }""")
    }

    "handle domain with single type" in {
      testRoundTrip("domain-type", """domain D is { type T is String }""")
    }

    "handle domain with multiple types" in {
      testRoundTrip("domain-types", """domain D is {
        type T1 is String
        type T2 is Number
        type T3 is Boolean
      }""")
    }

    // Level 2: Context
    "handle domain with empty context" in {
      testRoundTrip("domain-context", """domain D is {
        context C is { ??? }
      }""")
    }

    "handle context with type" in {
      testRoundTrip("context-type", """domain D is {
        context C is {
          type T is String
        }
      }""")
    }

    // Level 3: Entity
    "handle context with empty entity" in {
      testRoundTrip("context-entity", """domain D is {
        context C is {
          entity E is { ??? }
        }
      }""")
    }

    "handle entity with state" in {
      testRoundTrip("entity-state", """domain D is {
        context C is {
          entity E is {
            record Fields is { name: String }
            state S of E.Fields
          }
        }
      }""")
    }

    "handle entity with handler" in {
      testRoundTrip("entity-handler", """domain D is {
        context C is {
          entity E is {
            command DoIt is { ??? }
            handler H is {
              on command DoIt {
                error "not implemented"
              }
            }
          }
        }
      }""")
    }

    // Level 4: Adaptor (suspected problem area)
    "handle context with adaptor" in {
      testRoundTrip("context-adaptor", """domain D is {
        context C1 is { ??? }
        context C2 is {
          adaptor A from context C1 is { ??? }
        }
      }""")
    }

    "handle adaptor with handler" in {
      testRoundTrip("adaptor-handler", """domain D is {
        context C1 is {
          command Cmd is { ??? }
        }
        context C2 is {
          adaptor A from context C1 is {
            handler H is {
              on command Cmd {
                error "adapted"
              }
            }
          }
        }
      }""")
    }

    // Level 5: Multiple entities and adaptors
    "handle multiple entities in context" in {
      testRoundTrip("multi-entity", """domain D is {
        context C is {
          entity E1 is { ??? }
          entity E2 is { ??? }
          entity E3 is { ??? }
        }
      }""")
    }

    "handle entity and adaptor together" in {
      testRoundTrip("entity-and-adaptor", """domain D is {
        context C1 is { ??? }
        context C2 is {
          entity E is { ??? }
          adaptor A from context C1 is { ??? }
        }
      }""")
    }

    // Level 6: Metadata
    "handle domain with metadata" in {
      testRoundTrip("domain-metadata", """domain D is {
        type T is String
      } with {
        briefly as "A test domain"
      }""")
    }

    "handle entity with metadata" in {
      testRoundTrip("entity-metadata", """domain D is {
        context C is {
          entity E is { ??? } with {
            briefly as "An entity"
            option aggregate
          }
        }
      }""")
    }

    "handle adaptor with metadata" in {
      testRoundTrip("adaptor-metadata", """domain D is {
        context C1 is { ??? }
        context C2 is {
          adaptor A from context C1 is { ??? } with {
            briefly as "An adaptor"
          }
        }
      }""")
    }

    // Level 7: Streaming components
    "handle source and sink" in {
      testRoundTrip("source-sink", """domain D is {
        context C is {
          type Msg is command { data: String }
          source S is { outlet out is type Msg }
          sink K is { inlet in is type Msg }
        }
      }""")
    }

    "handle connector" in {
      testRoundTrip("connector", """domain D is {
        context C is {
          type Msg is command { data: String }
          source S is { outlet out is type Msg }
          sink K is { inlet in is type Msg }
          connector Conn from outlet S.out to inlet K.in
        }
      }""")
    }

    // Level 8: Functions and Sagas
    "handle function" in {
      testRoundTrip("function", """domain D is {
        context C is {
          function F is {
            requires { x: Integer }
            returns { y: Integer }
            ???
          }
        }
      }""")
    }

    "handle saga" in {
      testRoundTrip("saga", """domain D is {
        context C is {
          saga S is {
            step One is { prompt "do one" } reverted by { prompt "undo one" }
            step Two is { prompt "do two" } reverted by { prompt "undo two" }
          }
        }
      }""")
    }

    // Level 9: Repository
    "handle repository" in {
      testRoundTrip("repository", """domain D is {
        context C is {
          repository R is { ??? }
        }
      }""")
    }

    // Level 10: Complex combinations
    "handle complex context" in {
      testRoundTrip("complex-context", """domain D is {
        context C is {
          type T is String
          entity E is {
            record Fields is { name: String }
            state S of E.Fields
            handler H is {
              on other { error "unknown" }
            }
          }
          function F is { ??? }
          repository R is { ??? }
        }
      }""")
    }

    // Level 11: Multiline descriptions (from rbbq.riddl patterns)
    "handle multiline description" in {
      testRoundTrip("multiline-desc", """domain D is {
        type T is String
      } with {
        described as {
          |# Brief
          |A test domain
          |# Details
          |This is a detailed description
        }
      }""")
    }

    "handle explained metadata" in {
      testRoundTrip("explained", """domain D is {
        context C is {
          entity E is { ??? } with {
            explained as {
              |# Brief
              |An entity description
              |# Details
              |More details here
            }
          }
        }
      }""")
    }

    // Level 12: Id types and references
    "handle Id type" in {
      testRoundTrip("id-type", """domain D is {
        context C is {
          entity E is { ??? }
        }
        type MyId is Id(D.C.E)
      }""")
    }

    "handle reference type" in {
      testRoundTrip("ref-type", """domain D is {
        context C is {
          entity E is { ??? }
        }
        type MyRef is reference to entity D.C.E
      }""")
    }

    // Level 13: Pattern types
    "handle pattern type" in {
      testRoundTrip("pattern-type", """domain D is {
        type Phone is Pattern("[0-9]{3}-[0-9]{4}")
      }""")
    }

    // Level 14: Flow and connector (from dokn patterns)
    "handle flow with inlet and outlet" in {
      testRoundTrip("flow-io", """domain D is {
        context C is {
          type Msg is event { data: String }
          flow F is {
            inlet in is type Msg
            outlet out is type Msg
          }
        }
      }""")
    }

    "handle flow with connector" in {
      testRoundTrip("flow-connector", """domain D is {
        context C is {
          type Msg is event { data: String }
          flow F is {
            inlet in is type Msg
            outlet out is type Msg
          }
          connector Conn is {
            from outlet F.out to inlet F.in
          }
        }
      }""")
    }

    // Level 15: Alternation types - ISOLATING THE BUG
    "handle simple alternation" in {
      testRoundTrip("simple-alt", """domain D is {
        type Alt is one of { String or Number }
      }""")
    }

    "handle alternation with types" in {
      testRoundTrip("alt-types", """domain D is {
        type A is String
        type B is Number
        type Alt is one of { A or B }
      }""")
    }

    "handle alternation with event" in {
      testRoundTrip("alt-event", """domain D is {
        context C is {
          event Evt is { x: String }
          type Alt is one of { event Evt }
        }
      }""")
    }

    "handle event and command types in alternation" in {
      testRoundTrip("event-cmd", """domain D is {
        context C is {
          type AddCmd is command { name: String }
          type AddedEvt is event { when: TimeStamp }
          type CmdEvtUnion is one of { type AddCmd or type AddedEvt }
        }
      }""")
    }

    // Level 16: Entity with events and state (fixed - removed invalid outlet in entity body)
    "handle entity with event sourcing" in {
      testRoundTrip("entity-events", """domain D is {
        context C is {
          type Added is event { when: TimeStamp }
          source EventSource is { outlet out is type Added }
          entity E is {
            type Add is command { name: String }
            record Fields is { name: String }
            state S of E.Fields
            handler H is {
              on command Add {
                send event Added to outlet EventSource.out
              }
            }
          } with {
            option event-sourced
          }
        }
      }""")
    }

    // Level 17: Nested aggregates
    "handle nested aggregate types" in {
      testRoundTrip("nested-agg", """domain D is {
        type Inner is { x: Integer, y: Integer }
        type Outer is {
          inner: Inner,
          name: String,
          values: Integer*
        }
      }""")
    }

    // Level 18: Multiple contexts with cross-references
    "handle multiple contexts with references" in {
      testRoundTrip("multi-context-refs", """domain D is {
        context C1 is {
          type T1 is String
          entity E1 is { ??? }
        }
        context C2 is {
          type T2 is D.C1.T1
          adaptor A from context C1 is { ??? }
        }
      }""")
    }

    // Level 19: Test with dokn-like structure
    "handle dokn-like company entity" in {
      testRoundTrip("dokn-company", """domain dokn is {
        type Address is {
          line1: String,
          city: String,
          country: String
        }
        type CompanyId is Id(dokn.Companies.Company)

        context Companies is {
          flow events is {
            inlet CompanyEvents_in is event dokn.Companies.Company.CompanyEvent
            outlet CompanyEvents_out is event dokn.Companies.Company.CompanyEvent
          }
          connector CompanyEvents is {
            from outlet events.CompanyEvents_out to inlet events.CompanyEvents_in
          }

          entity Company is {
            type AddCompany is command { address: Address }
            event CompanyAdded is { when: TimeStamp }
            type CompanyEvent is one of { event CompanyAdded }

            record fields is { companyId: CompanyId, address: Address }
            state CompanyBase of Company.fields
            handler CompanyHandler is {
              on command AddCompany {
                send event CompanyAdded to outlet events.CompanyEvents_out
              }
            }
          } with {
            option event-sourced
            option available
          }
        }
      }""")
    }

    // Level 20: Test with rbbq-like structure
    "handle rbbq-like kitchen context" in {
      testRoundTrip("rbbq-kitchen", """domain ReactiveBBQ is {
        type CustomerId is Id(ReactiveBBQ.Customers.Customer) with {
          described as { "Customer identifier" }
        }
        type Empty is Nothing

        context Customers is {
          entity Customer is {
            state main of ReactiveBBQ.Empty
            handler Input is { ??? }
          }
        }

        context Kitchen is {
          type IP4Address is { a: Number, b: Number, c: Number, d: Number }
          type OrderViewType is { address: type IP4Address }
          entity OrderViewer is {
            record AField is { field: type OrderViewType }
            state OrderState of OrderViewer.AField
            handler Input is { ??? }
          } with {
            option is kind("device")
            explained as {
              |# Brief
              |This is an OrderViewer
              |# Details
              |The OrderViewer is the kitchen device
            }
          }
        } with {
          explained as {
            |# Brief
            |The kitchen context
            |# Details
            |Food preparation area
          }
        }
      }""")
    }
  }
}
