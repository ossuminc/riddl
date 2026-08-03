/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{AbstractTestingBasis, URL, pc}

/** An `include` is textual composition, not model structure: a definition means the same thing
  * wherever its text lives. These pin that the accessors agree, and -- just as important -- that
  * they do NOT over-report by descending into genuine nested containers.
  *
  * Domains are used throughout because a Domain may contain a Domain, so the same node type gives
  * both an include-wrapped case and an honestly-nested case.
  */
class IncludeTransparentAccessorsTest extends AbstractTestingBasis {

  private def domain(name: String, children: Seq[DomainContents] = Seq.empty): Domain =
    val contents = Contents.empty[DomainContents]()
    children.foreach(child => contents.append(child))
    Domain(At.empty, Identifier(At.empty, name), contents)
  end domain

  /** domain Outer { include { domain Included }  domain Direct } */
  private def outerWithIncludedDomain: Domain =
    val included = Contents.empty[OccursInDomain]()
    included.append(domain("Included"))
    val inc = Include[OccursInDomain](At.empty, URL.empty, included)
    domain("Outer", Seq(inc, domain("Direct")))
  end outerWithIncludedDomain

  /** domain Outer { domain Inner { domain Buried } } */
  private def outerWithNestedDomain: Domain =
    domain("Outer", Seq(domain("Inner", Seq(domain("Buried")))))

  "filterThroughIncludes" should {

    "see a definition that lives inside an Include" in {
      val found = outerWithIncludedDomain.contents.filterThroughIncludes[Domain]
      found.map(_.id.value).sorted mustBe Seq("Direct", "Included")
    }

    "NOT descend into containers that are not Include or BASTImport" in {
      // The whole point of not using recursiveFindByType: a domain nested inside a subdomain
      // is not a subdomain of THIS domain. Only "Inner" is, never "Buried".
      val found = outerWithNestedDomain.contents.filterThroughIncludes[Domain]
      found.map(_.id.value) mustBe Seq("Inner")
    }

    "leave plain filter alone -- it still means 'my direct children'" in {
      val found = outerWithIncludedDomain.contents.filter[Domain]
      found.map(_.id.value) mustBe Seq("Direct")
    }
  }

  "the named accessors" should {

    "make domain.domains see an included subdomain" in {
      outerWithIncludedDomain.domains.map(_.id.value).sorted mustBe Seq("Direct", "Included")
    }

    "keep domain.domains from reporting a subdomain's subdomain" in {
      outerWithNestedDomain.domains.map(_.id.value) mustBe Seq("Inner")
    }
  }
}
