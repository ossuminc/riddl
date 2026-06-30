/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Phase 1 of the JSON -> RIDDL AST input method. Each model must round-trip clean both ways:
  *   - parseJson -> validateRoot (no errors)
  *   - parseJson -> root2RiddlSource -> validateString (no errors)
  * plus defaults, builder-error, and undefined-reference coverage.
  */
class JsonInputTest extends AnyWordSpec with Matchers {

  /** Assert a model parses and validates with no errors both directly and through a prettify
    * round-trip. Warnings are allowed; errors are not.
    */
  private def assertRoundTrips(json: String): Unit =
    RiddlLib.parseJson(json) match
      case RiddlResult.Success(root) =>
        val vr = RiddlLib.validateRoot(root)
        withClue("validateRoot errors:\n" + vr.errors.map(_.format).mkString("\n") + "\n") {
          vr.errors mustBe empty
        }
        val riddl = RiddlLib.root2RiddlSource(root)
        val vr2 = RiddlLib.validateString(riddl)
        withClue(
          s"rendered RIDDL:\n$riddl\nvalidateString errors:\n" + vr2.errors
            .map(_.format)
            .mkString("\n")
        ) {
          vr2.errors mustBe empty
        }
      case RiddlResult.Failure(errors) =>
        fail("parseJson failed: " + errors.map(_.format).mkString("\n"))
    end match
  /** Assert a model parses and its rendered RIDDL re-parses (syntax-level round-trip). Used for
    * statements whose references are the JSON author's concern to resolve — here we only prove the
    * emitted RIDDL is well-formed.
    */
  private def assertRendersAndReparses(json: String): Unit =
    RiddlLib.parseJson(json) match
      case RiddlResult.Success(root) =>
        val riddl = RiddlLib.root2RiddlSource(root)
        RiddlLib.parseString(riddl) match
          case RiddlResult.Success(_) => ()
          case RiddlResult.Failure(errors) =>
            fail(
              s"rendered RIDDL did not re-parse:\n$riddl\n" + errors.map(_.format).mkString("\n")
            )
      case RiddlResult.Failure(errors) =>
        fail("parseJson failed: " + errors.map(_.format).mkString("\n"))
    end match
  /** Render a single field's type expression to RIDDL text (for defaults). */
  private def renderFieldType(typeExpr: String): String =
    val json =
      s"""{"domains":[{"name":"D","contexts":[{"name":"C","types":[
         |{"name":"T","typeExpression":{"kind":"Record","fields":[
         |{"name":"f","type":$typeExpr}]}}]}]}]}""".stripMargin
    RiddlLib.parseJson(json) match
      case RiddlResult.Success(root) => RiddlLib.root2RiddlSource(root)
      case RiddlResult.Failure(errors) =>
        fail("parseJson failed: " + errors.map(_.format).mkString("\n"))

  "JSON round-trips (Phase 1)" should {

    "a record, command, state, and handler with a `do` statement" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "Commerce", "brief": "online shopping",
          |  "contexts": [ { "name": "Orders",
          |    "types": [ { "name": "OrderInfo", "typeExpression": { "kind": "Record", "fields": [
          |      { "name": "sku", "type": { "kind": "String" } },
          |      { "name": "quantity", "type": { "kind": "Integer" } } ] } } ],
          |    "commands": [ { "name": "PlaceOrder", "brief": "place an order",
          |      "fields": [ { "name": "sku", "type": { "kind": "String", "max": 64 } } ] } ],
          |    "entities": [ { "name": "Order",
          |      "state": { "name": "current", "recordType": "OrderInfo" },
          |      "handlers": [ { "name": "Behavior", "onClauses": [
          |        { "kind": "message", "message": { "ref": "PlaceOrder", "kind": "command" },
          |          "statements": [ "record the order details" ] } ] } ] } ] } ] } ] }""".stripMargin
      )
    }

    "events, a query, a result, invariants, and init/other on-clauses" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "Billing", "contexts": [ { "name": "Invoicing",
          |  "events": [ { "name": "InvoiceIssued", "fields": [ { "name": "amount", "type": { "kind": "Decimal" } } ] } ],
          |  "queries": [ { "name": "GetInvoice", "fields": [ { "name": "id", "type": { "kind": "UUID" } } ] } ],
          |  "results": [ { "name": "InvoiceResult", "fields": [ { "name": "total", "type": { "kind": "Decimal", "whole": 10, "fractional": 2 } } ] } ],
          |  "types": [ { "name": "InvoiceData", "typeExpression": { "kind": "Record", "fields": [ { "name": "total", "type": { "kind": "Decimal" } } ] } } ],
          |  "entities": [ { "name": "Invoice",
          |    "state": { "name": "data", "recordType": "InvoiceData" },
          |    "invariants": [ { "name": "positive", "condition": "total > 0", "brief": "non-negative total" } ],
          |    "handlers": [ { "name": "H", "onClauses": [
          |      { "kind": "message", "message": { "ref": "InvoiceIssued", "kind": "event" }, "statements": [ "update totals" ] },
          |      { "kind": "init", "statements": [ "initialize the invoice" ] },
          |      { "kind": "other", "statements": [ "ignore" ] } ] } ] } ] } ] } ] }""".stripMargin
      )
    }

    "enum, pattern, alternation, alias, and all three cardinalities" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "Catalog", "contexts": [ { "name": "Products", "types": [
          |  { "name": "Color", "typeExpression": { "kind": "Enum", "values": [ "Red", "Green", "Blue" ] } },
          |  { "name": "Sku", "typeExpression": { "kind": "Pattern", "pattern": [ "^[A-Z]{3}-[0-9]+$" ] } },
          |  { "name": "Small", "typeExpression": { "kind": "Record", "fields": [ { "name": "a", "type": { "kind": "Integer" } } ] } },
          |  { "name": "Large", "typeExpression": { "kind": "Record", "fields": [ { "name": "b", "type": { "kind": "Integer" } } ] } },
          |  { "name": "Either", "typeExpression": { "kind": "Alternation", "of": [ "Small", "Large" ] } },
          |  { "name": "Product", "typeExpression": { "kind": "Record", "fields": [
          |    { "name": "color", "type": { "kind": "Alias", "ref": "Color" } },
          |    { "name": "sku", "type": { "kind": "Alias", "ref": "Sku" } },
          |    { "name": "tags", "type": { "cardinality": "zeroOrMore", "of": { "kind": "String" } } },
          |    { "name": "nickname", "type": { "cardinality": "optional", "of": { "kind": "String" } } },
          |    { "name": "variants", "type": { "cardinality": "oneOrMore", "of": { "kind": "Alias", "ref": "Either" } } } ] } } ] } ] } ] }""".stripMargin
      )
    }

    "an Id reference plus Currency, Range, Boolean, Date, and TimeStamp" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "Accounts", "contexts": [ { "name": "Customers",
          |  "types": [ { "name": "Profile", "typeExpression": { "kind": "Record", "fields": [
          |    { "name": "id", "type": { "kind": "Id", "entity": "Customer" } },
          |    { "name": "balance", "type": { "kind": "Currency" } },
          |    { "name": "score", "type": { "kind": "Range", "min": 0, "max": 850 } },
          |    { "name": "active", "type": { "kind": "Boolean" } },
          |    { "name": "born", "type": { "kind": "Date" } },
          |    { "name": "seen", "type": { "kind": "TimeStamp" } } ] } } ],
          |  "entities": [ { "name": "Customer",
          |    "state": { "name": "profile", "recordType": "Profile" },
          |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "init", "statements": [ "create the customer" ] } ] } ] } ] } ] } ] }""".stripMargin
      )
    }

    "a domain author and briefs at every level" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "Org", "brief": "the organization",
          |  "authors": [ { "name": "reid", "fullName": "Reid Spencer", "email": "reid@ossuminc.com",
          |    "organization": "Ossum Inc.", "title": "Architect" } ],
          |  "types": [ { "name": "Name", "brief": "a person name", "typeExpression": { "kind": "String", "min": 1, "max": 80 } } ],
          |  "contexts": [ { "name": "Core", "brief": "the core",
          |    "types": [ { "name": "Flag", "brief": "a boolean flag", "typeExpression": { "kind": "Boolean" } } ] } ] } ] }""".stripMargin
      )
    }
  }

  "JSON round-trips (Phase 2)" should {

    "collection and reference type expressions" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "P2c", "contexts": [ { "name": "C",
          |  "entities": [ { "name": "Anchor", "state": { "name": "s", "recordType": "R" },
          |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "init", "statements": [ "x" ] } ] } ] } ],
          |  "types": [ { "name": "R", "typeExpression": { "kind": "Record", "fields": [
          |    { "name": "seq", "type": { "kind": "Sequence", "of": { "kind": "Integer" } } },
          |    { "name": "set", "type": { "kind": "Set", "of": { "kind": "String" } } },
          |    { "name": "map", "type": { "kind": "Mapping", "from": { "kind": "String" }, "to": { "kind": "Integer" } } },
          |    { "name": "tab", "type": { "kind": "Table", "of": { "kind": "Integer" }, "dimensions": [ 2, 3 ] } },
          |    { "name": "gr", "type": { "kind": "Graph", "of": { "kind": "Integer" } } },
          |    { "name": "rep", "type": { "kind": "Replica", "of": { "kind": "Integer" } } },
          |    { "name": "ref", "type": { "kind": "EntityReference", "entity": "Anchor" } },
          |    { "name": "rng", "type": { "cardinality": "range", "of": { "kind": "String" }, "min": 1, "max": 5 } } ] } } ] } ] } ] }""".stripMargin
      )
    }

    "scalar/time/SI type expressions, plus a domain user, a constant, and valued enumerators" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "P2s",
          |  "users": [ { "name": "Shopper", "isA": "a person who shops" } ],
          |  "contexts": [ { "name": "C",
          |  "constants": [ { "name": "MaxItems", "type": { "kind": "Integer" }, "value": "100" } ],
          |  "types": [
          |    { "name": "Status", "typeExpression": { "kind": "Enum", "enumerators": [ { "name": "Off", "value": 0 }, { "name": "On", "value": 1 } ] } },
          |    { "name": "R", "typeExpression": { "kind": "Record", "fields": [
          |      { "name": "uri", "type": { "kind": "URI" } },
          |      { "name": "secureUri", "type": { "kind": "URI", "scheme": "https" } },
          |      { "name": "blob", "type": { "kind": "Blob", "blobKind": "JSON" } },
          |      { "name": "dur", "type": { "kind": "Duration" } },
          |      { "name": "t", "type": { "kind": "Time" } },
          |      { "name": "dt", "type": { "kind": "DateTime" } },
          |      { "name": "zd", "type": { "kind": "ZonedDate", "zone": "UTC" } },
          |      { "name": "zdt", "type": { "kind": "ZonedDateTime" } },
          |      { "name": "uid", "type": { "kind": "UserId" } },
          |      { "name": "loc", "type": { "kind": "Location" } },
          |      { "name": "len", "type": { "kind": "Length" } },
          |      { "name": "mass", "type": { "kind": "Mass" } } ] } } ] } ] } ] }""".stripMargin
      )
    }
  }

  "JSON round-trips (Phase 3)" should {

    "self-contained and control-flow statements (prompt/error/code/let/require/when/match)" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "P3a", "contexts": [ { "name": "C",
          |  "entities": [ { "name": "E", "state": { "name": "s", "recordType": "R" },
          |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "init", "statements": [
          |      "a bare-string prompt",
          |      { "kind": "prompt", "text": "a tagged prompt" },
          |      { "kind": "error", "message": "something went wrong" },
          |      { "kind": "code", "language": "scala", "body": "val x = 1" },
          |      { "kind": "let", "name": "x", "expression": "compute a value" },
          |      { "kind": "require", "condition": "x > 0" },
          |      { "kind": "when", "condition": "x > 0", "then": [ "do the then" ], "else": [ "do the else" ] },
          |      { "kind": "match", "expression": "x", "cases": [ { "pattern": "1", "statements": [ "matched one" ] } ], "default": [ "no match" ] }
          |    ] } ] } ] } ],
          |  "types": [ { "name": "R", "typeExpression": { "kind": "Record", "fields": [ { "name": "n", "type": { "kind": "Integer" } } ] } } ] } ] } ] }""".stripMargin
      )
    }

    "a function with input/output aggregations and a nested function" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "P3f", "contexts": [ { "name": "C",
          |  "functions": [ { "name": "calc", "brief": "compute a result",
          |    "input": [ { "name": "a", "type": { "kind": "Integer" } } ],
          |    "output": [ { "name": "r", "type": { "kind": "Integer" } } ],
          |    "statements": [ "compute r from a" ],
          |    "functions": [ { "name": "helper", "statements": [ "assist the computation" ] } ] } ] } ] } ] }""".stripMargin
      )
    }

    "a record with a method" in {
      assertRoundTrips(
        """{ "domains": [ { "name": "P3m", "contexts": [ { "name": "C", "types": [
          |  { "name": "R", "typeExpression": { "kind": "Record", "fields": [ { "name": "n", "type": { "kind": "Integer" } } ],
          |    "methods": [ { "name": "scaled", "type": { "kind": "Integer" }, "args": [ { "name": "by", "type": { "kind": "Integer" } } ] } ] } } ] } ] } ] }""".stripMargin
      )
    }

    "reference-carrying statements (set/send/tell/morph/become/require-invariant) render to valid RIDDL" in {
      assertRendersAndReparses(
        """{ "domains": [ { "name": "P3b", "contexts": [ { "name": "C",
          |  "entities": [ { "name": "Thing", "state": { "name": "Active", "recordType": "R" },
          |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "message",
          |      "message": { "ref": "Go", "kind": "command" }, "statements": [
          |        { "kind": "set", "field": "count", "value": "0" },
          |        { "kind": "set", "state": "Active", "value": "on" },
          |        { "kind": "send", "message": { "ref": "Notify", "kind": "command" }, "to": "Out", "portlet": "outlet" },
          |        { "kind": "tell", "message": { "ref": "Notify", "kind": "command" }, "to": "Other", "processor": "entity" },
          |        { "kind": "morph", "entity": "Thing", "state": "Active", "value": { "ref": "Switched", "kind": "event" } },
          |        { "kind": "become", "entity": "Thing", "handler": "H" },
          |        { "kind": "reply", "message": { "ref": "Done", "kind": "result" } },
          |        { "kind": "require", "invariant": "MustHold" }
          |      ] } ] } ] } ],
          |  "commands": [ { "name": "Go" } ],
          |  "types": [ { "name": "R", "typeExpression": { "kind": "Record", "fields": [ { "name": "count", "type": { "kind": "Integer" } } ] } } ] } ] } ] }""".stripMargin
      )
    }
  }

  "JSON defaults (Phase 1)" should {
    "String with no bounds renders String(0,255)" in {
      renderFieldType("""{ "kind": "String" }""") must include("String(0,255)")
    }
    "String with only max defaults min to 0" in {
      renderFieldType("""{ "kind": "String", "max": 99 }""") must include("String(0,99)")
    }
    "String with only min defaults max to 255" in {
      renderFieldType("""{ "kind": "String", "min": 5 }""") must include("String(5,255)")
    }
    "Decimal with no args renders Decimal(12,2)" in {
      renderFieldType("""{ "kind": "Decimal" }""") must include("Decimal(12,2)")
    }
    "Range with no args renders range(0,100)" in {
      renderFieldType("""{ "kind": "Range" }""") must include("range(0,100)")
    }
    "Currency with no country renders Currency(USD)" in {
      renderFieldType("""{ "kind": "Currency" }""") must include("Currency(USD)")
    }
  }

  "JSON builder errors (Phase 1)" should {

    def failsToBuild(typeExpr: String, expected: String): Unit =
      val json =
        s"""{"domains":[{"name":"D","contexts":[{"name":"C","types":[
           |{"name":"T","typeExpression":{"kind":"Record","fields":[
           |{"name":"f","type":$typeExpr}]}}]}]}]}""".stripMargin
      RiddlLib.parseJson(json) match
        case RiddlResult.Success(_) => fail(s"expected a Failure for $typeExpr")
        case RiddlResult.Failure(errors) =>
          withClue(errors.map(_.format).mkString("\n")) {
            errors.exists(_.format.toLowerCase.contains(expected)) mustBe true
          }
      end match

    "missing Id.entity is a clean Failure" in {
      failsToBuild("""{ "kind": "Id" }""", "entity")
    }
    "empty Enum is a clean Failure" in {
      failsToBuild("""{ "kind": "Enum", "values": [] }""", "enum")
    }
    "empty Pattern is a clean Failure" in {
      failsToBuild("""{ "kind": "Pattern", "pattern": [] }""", "pattern")
    }
    "malformed JSON is a clean Failure (not an exception)" in {
      RiddlLib.parseJson("{ this is not json }") match
        case RiddlResult.Success(_)      => fail("expected a Failure for malformed JSON")
        case RiddlResult.Failure(errors) => errors must not be empty
      end match
    }
  }

  "JSON undefined references (Phase 1)" should {
    "surface as validation errors, not builder errors" in {
      val json =
        """{ "domains": [ { "name": "D", "contexts": [ { "name": "C",
          |  "entities": [ { "name": "E", "state": { "name": "s", "recordType": "DoesNotExist" },
          |    "handlers": [ { "name": "H", "onClauses": [ { "kind": "init", "statements": [ "x" ] } ] } ] } ] } ] } ] }""".stripMargin
      RiddlLib.parseJson(json) match
        case RiddlResult.Success(root) =>
          val vr = RiddlLib.validateRoot(root)
          vr.errors must not be empty
        case RiddlResult.Failure(errors) =>
          fail(
            "parseJson should succeed (refs resolve later): " + errors.map(_.format).mkString("\n")
          )
      end match
    }
  }
}
