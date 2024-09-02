/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Context, Entity}
import com.ossuminc.riddl.language.parsing.ParsingTest

import org.scalatest.TestData

/** Unit Tests For Handler */
class HandlerTest extends ParsingTest {
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
    "accept an if statement " in { (td: TestData) =>
      val input = RiddlParserInput(
        """entity DistributionItem is {
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState
          |  handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      if  "==(field ContainerNestedInContainer.id, parentContainer)" then
          |        set field DistributionItem.lastKnownWOrkCenter to "field ContainerNestedInContainer.workCenter"
          |      else
          |        "nothing required"
          |      end
          |      explained as { "Helps update this item's location" }   
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
          |      "if ==(field ContainerNestedInContainer.id,parentContainer) then"
          |        set field DistributionItem.workCenter to "lastKnownWorkCenter"
          |      "end"
          |      explained as { "Helps update this item's location" } 
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
          |      "if rue == empty(timeOfFirstScan()) then"
          |        set field timeOfFirstScan to "field SortItem.originTimeStamp"
          |        set field journey to "field Sorted"
          |        "execute Unnest"
          |      "end"
          |    }
          |    on command RemoveItemFromContainer {
          |      set field journey to "field AtWorkCenter // ??? what's the correct journey?"
          |      set field parentContainer to "empty"
          |    }
          |    on command NestItem {
          |      "if ==(true,empty(timeOfFirstScan())) then"
          |        set field timeOfFirstScan to "field NestItem.originTimeStamp"
          |        set field parentContainer to "field NestItem.container"
          |        send command AddItemToContainer to inlet incoming
          |      "end"
          |    }
          |    on command TransportItem {
          |      "if ==(true,empty(timeOfFirstScan())) then"
          |        set field timeOfFirstScan to "field TransportItem.originTimeStamp"
          |        set field journey to "field TransportItem.InTransit"
          |        set field lastKnownWorkCenter to "field TransportItem.workCenter"
          |      "end"
          |    }
          |    on command ReceiveItem {
          |      "if ==(true,empty(timeOfFirstScan())) then"
          |         set field timeOfFirstScan to "field ReceiveItem.originTimeStamp"
          |         set field journey to "true"
          |         "execute Unnest"
          |      "end"
          |    }
          |    on command MarkItemOutForDelivery {
          |      set field journey to "field OutForDelivery"
          |    }
          |    on command DeliverItem {
          |      "set field journey to field Delivered"
          |      "execute Unnest"
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
  }
}
