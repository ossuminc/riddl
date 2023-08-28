/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Context
import com.reactific.riddl.language.AST.Entity

/** Unit Tests For Handler */
class HandlerTest extends ParsingTest {
  "Handlers" should {
    "be allowed in contexts" in {
      val input = """context Foo is {
                    |  type DoFoo is command { flux: Integer }
                    |  type FooDone is event { flux: Integer }
                    |  outlet begone is event FooDone
                    |  handler FooHandler is {
                    |    on command FooMessage {
                    |      send event FooDone( flux = "42" ) to outlet begone
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      parseDefinition[Context](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
    "empty example disallowed" in {
      val input = "handler foo is { on other { } "
      parseDefinition[Context](input) match {
        case Left(errors) =>
          errors must not(be(empty))
          succeed
        case Right(_) => fail("Did not catch empty on clause examples")
      }

    }
    "only one syntax error" in {
      val input =
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
          |                then morph entity Member to state Member.Active
          |                and set Active.memberId to @RegisterMember.memberId
          |                and set Active.memberInfo to @RegisterMember.memberInfo
          |            }
          |
          |        }
          |
          |        state Active is {
          |            memberId: MemberId,
          |            memberInfo: Info,
          |            metaInfo: MetaInfo
          |        }
          |        handler ActiveMemberHandler /*for state Active */ is {
          |            on command UpdateMemberInfo {
          |                then set Active.memberInfo to @UpdateMemberInfo.memberInfo
          |            }
          |            on command UpdateMemberStatus { ???
          |            }
          |            on query GetMemberData {  }
          |        }
          |
          |        state Terminated is {
          |            memberId: MemberId
          |        }
          |        handler TerminatedMemberHandler is {
          |            on other { then error "Terminated members cannot process messages" }
          |        }
          |    }
          |
          |}""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(errors) =>
          errors must not(be(empty))
          errors.size must be(1)
        case Right(_) => fail("Test case should have failed")
      }
    }
    "accept accept an if statement " in {
      val input =
        """entity DistributionItem is {
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState is { ??? }
          |  handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      if  ==(field ContainerNestedInContainer.id, "parentContainer") then
          |        set field DistributionItem.lastKnownWOrkCenter to field ContainerNestedInContainer.workCenter
          |      end
          |    } explained as { "Helps update this item's location" }
          |  }
          |}
          |""".stripMargin
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
    "handle statements" in {
      val input =
        """entity DistributionItem is {
          |  inlet incoming is event ItemPreInducted
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState is { ??? }
          | handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      if ==(field ContainerNestedInContainer.id,"parentContainer") then
          |        set field DistributionItem.workCenter to "lastKnownWorkCenter"
          |      end
          |    } explained as { "Helps update this item's location" }
          |    on other { "do nothing" }
          |  }
          |  handler FromDistributionItem  is {
          |    on command CreateItem {
          |      set field DistributionItem.journey to
          |        field FromDistributionItem.PreInducted
          |      set field DistributionItem.trackingId to
          |        field CreateItem.trackingId
          |      set field DistributionItem.manifestId to
          |        field CreateItem.manifestId
          |      set field DistributionItem.destination to
          |        field CreatItem.postalCode
          |      send event DistributionItem.ItemPreInducted to
          |        inlet DistributionItem.incoming
          |    }
          |    on command InductItem {
          |      set field DistributionItem.timeOfFirstScan to
          |        field InductItem.originTimeStamp
          |      set field DistributionItem.journey to
          |        field InductItem.Inducted
          |      set field DistributionItem.lastKnownWorkCenterId to
          |        field InductItem.workCenter
          |      send event DistributionItem.ItemInducted() to
          |        inlet DistributionItem.incoming
          |    }
          |    on command SortItem {
          |      if ==(true,empty(timeOfFirstScan())) then
          |        set field timeOfFirstScan to field SortItem.originTimeStamp
          |        set field journey to field Sorted
          |        "execute Unnest"
          |      end
          |    }
          |    on command RemoveItemFromContainer {
          |      set field journey to field AtWorkCenter // ??? what's the correct journey?
          |      set field parentContainer to "empty"
          |    }
          |    on command NestItem {
          |      if ==(true,empty(timeOfFirstScan())) then
          |        set field timeOfFirstScan to field NestItem.originTimeStamp
          |        set field parentContainer to field NestItem.container
          |        send command AddItemToContainer() to inlet incoming
          |      end
          |    }
          |    on command TransportItem {
          |      if ==(true,empty(timeOfFirstScan())) then
          |        set field timeOfFirstScan to field TransportItem.originTimeStamp
          |        set field journey to field TransportItem.InTransit
          |        set field lastKnownWorkCenter to field TransportItem.workCenter
          |      end
          |    }
          |    on command ReceiveItem {
          |      if ==(true,empty(timeOfFirstScan())) then
          |        set field timeOfFirstScan to field ReceiveItem.originTimeStamp
          |        set field journey to true
          |        "execute Unnest"
          |      end
          |    }
          |    // TODO: what commands bring item out of a hold?
          |    on command MarkItemOutForDelivery {
          |      set field journey to field OutForDelivery
          |    }
          |    on command DeliverItem {
          |      set field journey to field Delivered
          |      "execute Unnest"
          |    }
          |    on command MachineMissort {
          |      set field journey to unknown() // TODO: how do we respond to this?
          |    }
          |    on command HumanMissort {
          |      set field journey to unknown() // TODO: how do we respond to this?
          |    }
          |    on command CustomerAddressingError {
          |      set field journey to onHold() // TODO: how do we respond to this?
          |    }
          |  }
          |}
          |""".stripMargin
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString("\n")
          fail(msg)
        case Right(_) => succeed
      }
    }
  }
}
