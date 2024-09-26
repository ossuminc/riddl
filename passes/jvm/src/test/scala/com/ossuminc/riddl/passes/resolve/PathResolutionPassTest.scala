package com.ossuminc.riddl.passes.resolve

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path
import org.scalatest.{Assertion, TestData}

/** Unit Tests For the ResolutionPass */
class PathResolutionPassTest extends ResolvingTest {

  "PathResolutionPass" must {
    "resolve rbbq.riddl" in { (td: TestData) =>
      val input = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/domains/rbbq.riddl"), td)
      parseAndResolve(input) { (_, _) => succeed }
    }
    "resolves everything in rbbq.riddl" in { (td: TestData) =>
      val input = RiddlParserInput.fromCwdPath(Path.of("passes/jvm/src/test/input/rbbq.riddl"))
      def onSuccess(in: PassInput, out: PassesOutput): Assertion =
        val refMap = out.resolution.refMap
        refMap.definitionOf[Entity]("ReactiveBBQ.Customer.Customer") must not be (empty)
        refMap.definitionOf[Type]("ReactiveBBQ.Empty") must not be (empty)
        refMap.definitionOf[Type]("IP4Address") must not be (empty)
        refMap.definitionOf[Type]("OrderViewType") must not be (empty)
        refMap.definitionOf[Type]("OrderViewer.fields") must not be (empty)
        refMap.definitionOf[Type]("CustomerId") must not be (empty)
        refMap.definitionOf[Type]("OrderId") must not be (empty)
        refMap.definitionOf[Type]("AccrualEvent") must not be (empty)
        refMap.definitionOf[Type]("AwardEvent") must not be (empty)
        refMap.definitionOf[Type]("RewardEvent") must not be (empty)
        refMap.definitionOf[Context]("ReactiveBBQ.Payment") must not be (empty)
        refMap.definitionOf[Type]("Order.fields") must not be (empty)
        refMap.definitionOf[Type]("Payment.fields") must not be (empty)
        refMap.definitionOf[Entity]("MenuItem") must not be (empty)
        refMap.definitionOf[Type]("MenuItem.fields") must not be (empty)
        refMap.definitionOf[Type]("MenuItemRef") must not be (empty)
        refMap.definitionOf[Entity]("Location") must not be (empty)
        refMap.definitionOf[Type]("Reservation.fields") must not be (empty)
        refMap.definitionOf[Type]("ReservationValue") must not be (empty)
      end onSuccess

      def onFailure(messages: Messages): Assertion = fail(messages.justErrors.format)

      parseAndResolve(input)(onSuccess)(onFailure)
    }
    "resolves everything in dokn.riddl" in { (td: TestData) =>
      val input = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/dokn.riddl"))

      def onSuccess(in: PassInput, out: PassesOutput): Assertion =
        val refMap = out.resolution.refMap
        refMap.definitionOf[Entity]("dokn.Company.Company") must not be (empty)
        refMap.definitionOf[Type]("MobileNumber") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Note.Note") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Media.Media") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Location.Location") must not be (empty)
        refMap.definitionOf[Entity]("dokn.Note.Note") must not be (empty)
        refMap.definitionOf[Type]("dokn.Company.Company.CompanyEvent") must not be (empty)
        refMap.definitionOf[Outlet]("CompanyEvents_out") must not be (empty)
        refMap.definitionOf[Inlet]("CompanyEvents_in") must not be (empty)
        refMap.definitionOf[Type]("Address") must not be (empty)
        refMap.definitionOf[Type]("EmailAddress") must not be (empty)
        refMap.definitionOf[Type]("CompanyAdded") must not be (empty)
        refMap.definitionOf[Type]("Company.fields") must not be (empty)
        refMap.definitionOf[Type]("AddCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverCommands") must not be (empty)
        refMap.definitionOf[Type]("DriverEvents") must not be (empty)
        refMap.definitionOf[Type]("Driver.fields") must not be (empty)
        refMap.definitionOf[Type]("AddDriverToCompany") must not be (empty)
        refMap.definitionOf[Type]("RemoveDriverFromCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverAddedToCompany") must not be (empty)
        refMap.definitionOf[Type]("DriverRemovedFromCompany") must not be (empty)
        refMap.definitionOf[Outlet]("Driver_out") must not be (empty)
        refMap.definitionOf[Type]("LocationId") must not be (empty)
        refMap.definitionOf[Type]("Note.fields") must not be (empty)
        refMap.definitionOf[Type]("Media.fields") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
        refMap.definitionOf[Type]("NoteList") must not be (empty)
        refMap.definitionOf[Inlet]("CompanyEvents_in") must not be (empty)
        refMap.definitionOf[Field]("LocationBase.address") must not be (empty)
        refMap.definitionOf[Inlet]("Driver_in") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
        refMap.definitionOf[Type]("dokn.Address") must not be (empty)
      end onSuccess

      def onFailure(messages: Messages): Assertion = fail(messages.justErrors.format)

      parseAndResolve(input)(onSuccess)(onFailure)
    }
  }
}
