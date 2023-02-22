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
    "allowed in contexts" in {
      val input = """context Foo is {
                    |  type DoFoo is command { flux: Integer }
                    |  type FooDone is event { flux: Integer }
                    |  outlet begone is event FooDone
                    |  handler FooHandler is {
                    |    on command FooMessage {
                    |      then send event FooDone( flux = 42 ) to outlet begone
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
    "accept shortcut syntax for single example on clauses " in {
      val input =
        """entity DistributionItem is {
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState is { ??? }
          |  handler FromContainer  is {
          |    on event ContainerNestedInContainer {
          |      when ==(@ContainerNestedInContainer.id,@parentContainer)
          |      then set lastKnownWorkCenter to @ContainerNestedInContainer.workCenter
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
    "handle actions" in {
      val input =
        """entity DistributionItem is {
          |  inlet incoming is event ItemPreInducted
          |  type ArbitraryState is { value: String }
          |  state DistributionState of ArbitraryState is { ??? }
          | handler FromContainer  is {
          |    on event ContainerNestedInContainer { example only {
          |      when ==(@ContainerNestedInContainer.id,@parentContainer)
          |      then set lastKnownWorkCenter to @ContainerNestedInContainer.workCenter
          |      }
          |      // anything else needing to be updated?
          |    } explained as { "Helps update this item's location" }
          |    on other { then "do nothing" }
          |  }
          |  handler FromDistributionItem  is {
          |    on command CreateItem { example only {
          |      // intent: DistributionItem is created
          |      then set journey to @PreInducted
          |      and set trackingId to @CreateItem.trackingId
          |      and set manifestId to @CreateItem.manifestId
          |      and set destination to @CreatItem.postalCode
          |      and send event ItemPreInducted() to inlet incoming
          |    } }
          |    on command InductItem { example only {
          |      then set timeOfFirstScan to @InductItem.originTimeStamp
          |      and set journey to @Inducted
          |      and set lastKnownWorkCenterId to @InductItem.workCenter
          |      and send event ItemInducted() to inlet incoming
          |    } }
          |    on command SortItem { example only {
          |      when empty(what=@timeOfFirstScan)
          |      then set timeOfFirstScan to @SortItem.originTimeStamp
          |      and set journey to @Sorted
          |      and set lastKnownWorkCenter to @SortItem.workCenter
          |      and "execute Unnest"
          |    }}
          |    on command RemoveItemFromContainer { example only {
          |      then set journey to @AtWorkCenter // ??? what's the correct journey?
          |      and set parentContainer to empty()
          |    }}
          |    on command NestItem { example only {
          |      when empty(what=@timeOfFirstScan)
          |      then set timeOfFirstScan to @NestItem.originTimeStamp
          |      and set parentContainer to @NestItem.container
          |      and send command AddItemToContainer() to inlet incoming
          |    }}
          |    on command TransportItem { example only {
          |      when empty(what=timeOfFirstScan())
          |      then set timeOfFirstScan to @TransportItem.originTimeStamp
          |      and set journey to InTransit(trip = @TransportItem.tripId)
          |      and set lastKnownWorkCenter to @TransportItem.workCenter
          |    }}
          |    on command ReceiveItem { example only {
          |      when empty(what=@timeOfFirstScan)
          |      then set timeOfFirstScan to @ReceiveItem.originTimeStamp
          |      and set journey to AtWorkCenter(workCenter=@ReceiveItem.workCenter)
          |      and "execute Unnest"
          |    } }
          |    // TODO: what commands bring item out of a hold?
          |    on command MarkItemOutForDelivery { example only {
          |      then set journey to @OutForDelivery
          |    }}
          |    on command DeliverItem { example only {
          |      then set journey to @Delivered
          |      and "execute Unnest"
          |    }}
          |    on command MachineMissort { example only {
          |      then set journey to unknown() // TODO: how do we respond to this?
          |    }}
          |    on command HumanMissort { example only {
          |      then set journey to unknown() // TODO: how do we respond to this?
          |    }}
          |    on command CustomerAddressingError { example only {
          |      then set journey to @OnHold // TODO: how do we respond to this?
          |    }}
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
