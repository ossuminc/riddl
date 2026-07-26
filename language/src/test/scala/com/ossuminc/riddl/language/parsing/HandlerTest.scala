/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{
  Context,
  Entity,
  LiteralString,
  OnActivationClause,
  OnEventClause,
  OnMessageClause,
  OnPassivationClause,
  RequireStatement
}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.AbstractParsingTest
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Unit Tests For Handler */
abstract class HandlerTest(using PlatformContext) extends AbstractParsingTest {
  "Handlers" should {
    "be allowed in contexts" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context Foo is {
          |  type DoFoo is command { flux: Integer }
          |  type FooDone is event { flux: Integer }
          |  handler FooHandler is {
          |    on command FooMessage {
          |      send event FooDone to outlet begone
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }

    "empty example disallowed" in { (td: TestData) =>
      val input = RiddlParserInput("handler foo is { on other { } ", td)
      parseDefinition[Context](input) match {
        case Left(errors) =>
          errors must not(be(empty))
          succeed
        case Right(_) => fail("Did not catch empty on clause examples")
      }
    }

    "only one syntax error" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain foo is {
          |context Members is {
          |
          |    type RegisterMember is command {}
          |    type MemberRegistered is event {}
          |    type RegisterMemberList is command {}
          |    type MemberListRegistered is event {}
          |    type UpdateMemberInfo is command {}
          |    type MemberInfoUpdated is event {}
          |    type UpdateMemberStatus is command {}
          |    type MemberStatusUpdated is event {}
          |    type GetMemberData is query {}
          |    type MemberData is result {}
          |    type GetMembersByMetaInfo is query {}
          |    type MemberListResult is result {}
          |
          |    entity Member is {
          |        option is aggregate
          |
          |        handler MemberHandler is {
          |            on command RegisterMember {
          |                morph entity Member to state Member.Active
          |                set Active.memberId to "RegisterMember.memberId"
          |                set Active.memberInfo to "RegisterMember.memberInfo"
          |            }
          |        }
          |
          |        state Active is {
          |            memberId: MemberId,
          |            memberInfo: Info,
          |            metaInfo: MetaInfo
          |        }
          |        handler ActiveMemberHandler /*for state Active */ is {
          |            on command UpdateMemberInfo {
          |                set Active.memberInfo to "UpdateMemberInfo.memberInfo"
          |            }
          |            on command UpdateMemberStatus { ??? }
          |            on query GetMemberData {  ??? }
          |        }
          |
          |        state Terminated is {
          |            memberId: MemberId
          |        }
          |        handler TerminatedMemberHandler is {
          |            on other { error "Terminated members cannot process messages" }
          |        }
          |    }
          |
          |}""".stripMargin,
        td
      )
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          errors must not(be(empty))
          errors.size must be(1)
        case Right(_) => fail("Test case should have failed")
      }
    }
    "accept a when statement " in { (td: TestData) =>
      val input = RiddlParserInput(
        """entity DistributionItem is {
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState
          |  handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      when "==(field ContainerNestedInContainer.id, parentContainer)" then
          |        set field DistributionItem.lastKnownWOrkCenter to "field ContainerNestedInContainer.workCenter"
          |      end
          |    } with {
          |      described as "Helps update this item's location"
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
    "handle statements" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context Contextual is {
          |  sink foo is {
          |    inlet incoming is event ItemPreInducted
          |  }
          |entity DistributionItem is {
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState
          | handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      when "==(field ContainerNestedInContainer.id,parentContainer)" then
          |        set field DistributionItem.workCenter to "lastKnownWorkCenter"
          |      end
          |    } with {
          |      described as "Helps update this item's location"
          |    }
          |    on other is { ??? }
          |  }
          |  handler FromDistributionItem  is {
          |    on command CreateItem {
          |      set field DistributionItem.journey to "field FromDistributionItem.PreInducted"
          |      set field DistributionItem.trackingId to "field CreateItem.trackingId"
          |      set field DistributionItem.manifestId to "field CreateItem.manifestId"
          |      set field DistributionItem.destination to "field CreatItem.postalCode"
          |      send event DistributionItem.ItemPreInducted to inlet Contextual.foo.incoming
          |    }
          |    on command InductItem {
          |      set field DistributionItem.timeOfFirstScan to "field InductItem.originTimeStamp"
          |      set field DistributionItem.journey to "field InductItem.Inducted"
          |      set field DistributionItem.lastKnownWorkCenterId to "field InductItem.workCenter"
          |      send event DistributionItem.ItemInducted to inlet DistributionItem.incoming
          |    }
          |    on command SortItem {
          |      when "rue == empty(timeOfFirstScan())" then
          |        set field timeOfFirstScan to "field SortItem.originTimeStamp"
          |        set field journey to "field Sorted"
          |        prompt "execute Unnest"
          |      end
          |    }
          |    on command RemoveItemFromContainer {
          |      set field journey to "field AtWorkCenter // ??? what's the correct journey?"
          |      set field parentContainer to "empty"
          |    }
          |    on command NestItem {
          |      when "==(true,empty(timeOfFirstScan()))" then
          |        set field timeOfFirstScan to "field NestItem.originTimeStamp"
          |        set field parentContainer to "field NestItem.container"
          |        send command AddItemToContainer to inlet incoming
          |      end
          |    }
          |    on command TransportItem {
          |      when "==(true,empty(timeOfFirstScan()))" then
          |        set field timeOfFirstScan to "field TransportItem.originTimeStamp"
          |        set field journey to "field TransportItem.InTransit"
          |        set field lastKnownWorkCenter to "field TransportItem.workCenter"
          |      end
          |    }
          |    on command ReceiveItem {
          |      when "==(true,empty(timeOfFirstScan()))" then
          |         set field timeOfFirstScan to "field ReceiveItem.originTimeStamp"
          |         set field journey to "true"
          |         prompt "execute Unnest"
          |      end
          |    }
          |    on command MarkItemOutForDelivery {
          |      set field journey to "field OutForDelivery"
          |    }
          |    on command DeliverItem {
          |      prompt "set field journey to field Delivered"
          |      prompt "execute Unnest"
          |    }
          |    on command MachineMissort {
          |      set field journey to "unknown()"
          |    }
          |    on command HumanMissort {
          |      set field journey to "unknown()"
          |    }
          |    on command CustomerAddressingError {
          |      set field journey to "onHold()"
          |    }
          |  }
          |}
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
    "accept require statements" in { (td: TestData) =>
      val input = RiddlParserInput(
        """entity Account is {
          |  type AccountState is { balance: Number }
          |  state Active of Account.AccountState
          |  handler Transactions is {
          |    on command Withdraw {
          |      require "balance >= amount"
          |      set field Account.balance to "balance - amount"
          |    }
          |    on command Transfer {
          |      require "balance >= amount"
          |      require "recipient != sender"
          |      prompt "execute transfer"
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right((entity, _)) =>
          val handler = entity.handlers.head
          val clause = handler.clauses.head
          val finder = Finder(clause.contents)
          val requires = finder.findByType[RequireStatement]
          requires.size must be(1)
          requires.head.condition match {
            case ls: LiteralString => ls.s must be("balance >= amount")
            case _                 => fail("Expected LiteralString condition")
          }
          // Second clause has two requires
          val clause2 = handler.clauses(1)
          val finder2 = Finder(clause2.contents)
          val requires2 = finder2.findByType[RequireStatement]
          requires2.size must be(2)
          succeed
      }
    }

    // ---- Handler kinds (2.0): event-only projectors, entity lifecycle clauses,
    // and the parse-time statement bans that make those distinctions structural. ----

    "parse entity 'on activate' / 'on passivate' lifecycle clauses" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context c is {
          |  entity e is {
          |    command Cmd is { g: Integer }
          |    handler h is {
          |      on command Cmd { prompt "handle" }
          |      on activate { prompt "rehydrate" }
          |      on passivate { prompt "evict" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => fail(errors.map(_.format).mkString("\n"))
        case Right((context, _)) =>
          val finder = Finder(context)
          finder.recursiveFindByType[OnActivationClause].size must be(1)
          finder.recursiveFindByType[OnPassivationClause].size must be(1)
          succeed
      }
    }

    "parse 'on event' as an OnEventClause, distinct from OnMessageClause" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context c is {
          |  entity e is {
          |    event Evt is { g: Integer }
          |    handler h is {
          |      on event Evt { prompt "note" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => fail(errors.map(_.format).mkString("\n"))
        case Right((context, _)) =>
          val finder = Finder(context)
          finder.recursiveFindByType[OnEventClause].size must be(1)
          finder.recursiveFindByType[OnMessageClause] mustBe empty
          succeed
      }
    }

    "reject 'on command' in a projector at parse time (projectors are event-only)" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """context c is {
            |  projector p is {
            |    command Cmd is { g: Integer }
            |    handler h is {
            |      on command Cmd { prompt "x" }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseDefinition[Context](input) match {
          case Left(errors) => errors.map(_.format).mkString must include("event-only")
          case Right(_)     => fail("projector accepted an 'on command' clause")
        }
    }

    "reject 'require' in an 'on event' clause at parse time (events must be accepted)" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """context c is {
            |  entity e is {
            |    event Evt is { g: Integer }
            |    handler h is {
            |      on event Evt { require "g > 0" }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseDefinition[Context](input) match {
          case Left(errors) => errors.map(_.format).mkString must include("always be accepted")
          case Right(_)     => fail("'on event' accepted a 'require' statement")
        }
    }

    "reject 'on activate' outside an entity at parse time" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context c is {
          |  handler h is {
          |    on activate { prompt "x" }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => errors.map(_.format).mkString must include("only allowed in entity")
        case Right(_)     => fail("context accepted an 'on activate' clause")
      }
    }

    "reject 'on query' and 'on record' in a projector at parse time" in { (td: TestData) =>
      rejectsParse(
        """context c is {
          |  projector p is {
          |    query Q is { g: Integer }
          |    handler h is { on query Q { prompt "x" } }
          |  }
          |}""".stripMargin,
        "event-only",
        "projector accepted an 'on query' clause"
      )(td)
      rejectsParse(
        """context c is {
          |  projector p is {
          |    record R is { g: Integer }
          |    handler h is { on record R { prompt "x" } }
          |  }
          |}""".stripMargin,
        "event-only",
        "projector accepted an 'on record' clause"
      )(td)
    }

    "accept 'on event' and 'on result' in a projector at parse time" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context c is {
          |  projector p is {
          |    event Evt is { g: Integer }
          |    result Res is { h: Integer }
          |    handler hh is {
          |      on event Evt { prompt "e" }
          |      on result Res { prompt "r" }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => fail(errors.map(_.format).mkString("\n"))
        case Right((context, _)) =>
          val finder = Finder(context)
          finder.recursiveFindByType[OnEventClause].size must be(1)
          finder.recursiveFindByType[OnMessageClause].size must be(1) // the 'on result Res'
          succeed
      }
    }

    "reject 'on passivate' outside an entity at parse time" in { (td: TestData) =>
      rejectsParse(
        """context c is {
          |  handler h is { on passivate { prompt "x" } }
          |}""".stripMargin,
        "only allowed in entity",
        "context accepted an 'on passivate' clause"
      )(td)
    }

    "reject 'error' in an 'on event' clause at parse time" in { (td: TestData) =>
      rejectsParse(
        """context c is {
          |  entity e is {
          |    event Evt is { g: Integer }
          |    handler h is { on event Evt { error "bad" } }
          |  }
          |}""".stripMargin,
        "always be accepted",
        "'on event' accepted an 'error' statement"
      )(td)
    }

    "reject all outbound messaging (send/tell/reply/morph/become) in an 'on activate' clause" in {
      (td: TestData) =>
        def onActivate(stmt: String): String =
          s"""context c is {
             |  entity e is {
             |    event Evt is { g: Integer }
             |    handler h is { on activate { $stmt } }
             |  }
             |}""".stripMargin
        // The rejecter fires on the leading keyword, so partial statement syntax is fine.
        rejectsParse(
          onActivate("send event Evt to inlet c.p.in"),
          "side-effect-free",
          "activate: send"
        )(td)
        rejectsParse(
          onActivate("tell event Evt to entity e"),
          "side-effect-free",
          "activate: tell"
        )(td)
        rejectsParse(onActivate("reply event Evt"), "side-effect-free", "activate: reply")(td)
        rejectsParse(
          onActivate("morph entity e to state e.s with event Evt"),
          "side-effect-free",
          "activate: morph"
        )(td)
        rejectsParse(
          onActivate("become entity e to handler h"),
          "side-effect-free",
          "activate: become"
        )(td)
    }
  }

  /** Assert that parsing `src` as a Context fails and its error mentions `substring`. */
  private def rejectsParse(src: String, substring: String, whatIfAccepted: String)(
    td: TestData
  ): org.scalatest.Assertion =
    parseDefinition[Context](RiddlParserInput(src, td)) match {
      case Left(errors) => errors.map(_.format).mkString must include(substring)
      case Right(_)     => fail(whatIfAccepted)
    }
}
