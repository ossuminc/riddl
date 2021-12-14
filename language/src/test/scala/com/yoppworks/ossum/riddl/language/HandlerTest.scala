package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Entity

/** Unit Tests For HandlerTest */
class HandlerTest extends ParsingTest {
  "Handlers" should {
    "handle actions" in {
      val input = """entity DistributionItem is {
                    |  state DistributionState is {}
                    | handler FromContainer  is {
                    |    on event ContainerNestedInContainer {
                    |      when ==(ContainerNestedInContainer.id,parentContainer)
                    |      then {
                    |        set lastKnownWorkCenter to ContainerNestedInContainer.workCenter
                    |      }
                    |      // anything else needing to be updated?
                    |    } explained as { "Helps update this item's location" }
                    |  }
                    |  handler FromDistributionItem  is {
                    |    on command CreateItem {
                    |      // intent: DistributionItem is created
                    |      set journey to PreInducted
                    |      set trackingId to CreateItem.trackingId
                    |      set manifestId to CreateItem.manifestId
                    |      set destination to CreatItem.postalCode
                    |      publish event ItemPreInducted() to topic DistributionItemTopic
                    |    }
                    |    on command InductItem {
                    |      set timeOfFirstScan to InductItem.originTimeStamp
                    |      set journey to Inducted
                    |      set lastKnownWorkCenterId to InductItem.workCenter
                    |      publish event ItemInducted() to topic DistributionItemTopic
                    |    }
                    |    on command SortItem {
                    |      when empty(timeOfFirstScan) then { set
                    |      timeOfFirstScan to
                    |      SortItem.originTimeStamp }
                    |      set journey to Sorted
                    |      set lastKnownWorkCenter to SortItem.workCenter
                    |      execute Unnest
                    |    }
                    |    on command RemoveItemFromContainer { // received from Container
                    |      set journey to AtWorkCenter // ??? what's the correct journey?
                    |      set parentContainer to empty
                    |    }
                    |    on command NestItem {
                    |      when empty(timeOfFirstScan) then {
                    |        set timeOfFirstScan to NestItem.originTimeStamp
                    |      }
                    |      set parentContainer to NestItem.container
                    |      publish command AddItemToContainer() to topic ContainerTopic
                    |    }
                    |    on command TransportItem {
                    |      when empty(timeOfFirstScan) { set timeOfFirstScan to
                    |      TransportItem.originTimeStamp }
                    |      set journey to InTransit(trip = TransportItem.tripId)
                    |      set lastKnownWorkCenter to TransportItem.workCenter
                    |    }
                    |    on command ReceiveItem {
                    |      when empty(timeOfFirstScan)  { set timeOfFirstScan to
                    |      ReceiveItem.originTimeStamp }
                    |      set journey to AtWorkCenter(
                    |        workCenter = ReceiveItem.workCenter
                    |      )
                    |      execute Unnest
                    |    }
                    |    // TODO: what commands bring item out of a hold?
                    |    on command MarkItemOutForDelivery {
                    |      set journey to OutForDelivery
                    |    }
                    |    on command DeliverItem {
                    |      set journey to Delivered
                    |      execute Unnest
                    |    }
                    |    on command MachineMissort {
                    |      set journey to ??? // TODO: how do we respond to this?
                    |    }
                    |    on command HumanMissort {
                    |      set journey to ??? // TODO: how do we respond to this?
                    |    }
                    |    on command CustomerAddressingError {
                    |      set journey to OnHold // TODO: how do we respond to this?
                    |    }
                    |  }
                    |}
                    |""".stripMargin
      parseDefinition[Entity](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(content) => succeed
      }
    }
  }
}
