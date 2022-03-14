package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Entity
import com.reactific.riddl.language.testkit.ParsingTest

/** Unit Tests For HandlerTest */
class HandlerTest extends ParsingTest {
  "Handlers" should {
    "accept shortcut syntax for single example on clauses " in {
      val input = """entity DistributionItem is {
                    |  state DistributionState is { ??? }
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
      val input = """entity DistributionItem is {
                    |  state DistributionState is { ??? }
                    | handler FromContainer  is {
                    |    on event ContainerNestedInContainer { example only {
                    |      when ==(@ContainerNestedInContainer.id,@parentContainer)
                    |      then set lastKnownWorkCenter to @ContainerNestedInContainer.workCenter
                    |      }
                    |      // anything else needing to be updated?
                    |    } explained as { "Helps update this item's location" }
                    |  }
                    |  handler FromDistributionItem  is {
                    |    on command CreateItem { example only {
                    |      // intent: DistributionItem is created
                    |      then set journey to @PreInducted
                    |      and set trackingId to @CreateItem.trackingId
                    |      and set manifestId to @CreateItem.manifestId
                    |      and set destination to @CreatItem.postalCode
                    |      and tell event ItemPreInducted() to entity DistributionItem
                    |    } }
                    |    on command InductItem { example only {
                    |      then set timeOfFirstScan to @InductItem.originTimeStamp
                    |      and set journey to @Inducted
                    |      and set lastKnownWorkCenterId to @InductItem.workCenter
                    |      and tell event ItemInducted() to entity DistributionItem
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
                    |      and tell command AddItemToContainer() to entity DistributionItem
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
