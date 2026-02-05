/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.transforms

import com.ossuminc.riddl.language.{At, Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.AbstractTestingBasis

class FlattenPassTest extends AbstractTestingBasis {

  private def mkId(name: String): Identifier = Identifier(At(), name)
  private def mkDomain(name: String, items: DomainContents*): Domain =
    Domain(At(), mkId(name), Contents(items*))
  private def mkContext(name: String, items: ContextContents*): Context =
    Context(At(), mkId(name), Contents(items*))
  private def mkEntity(name: String): Entity =
    Entity(At(), mkId(name))

  /** Build a Root whose contents include Includes and/or BASTImports.
    * We build via an empty buffer and append with asInstanceOf because
    * Include[T] is not a member of the RootContents union type directly.
    */
  private def mkRoot(items: RiddlValue*): Root =
    val buf = Contents.empty[RootContents]()
    items.foreach(i => buf.append(i.asInstanceOf[RootContents]))
    Root(At(), buf)
  end mkRoot

  private def mkBASTImport(items: NebulaContents*): BASTImport =
    BASTImport(
      loc = At(),
      path = LiteralString(At(), "test.bast"),
      contents = Contents(items*)
    )

  "Container.flatten()" should {

    "flatten a single Include at root level" in {
      val domain = mkDomain("Foo")
      val include = Include[Domain](At(), contents = Contents(domain))
      val root = mkRoot(include)

      root.flatten()

      assert(root.contents.toSeq.size == 1)
      assert(root.contents.toSeq.head.isInstanceOf[Domain])
      assert(root.domains.head.id.value == "Foo")
    }

    "flatten a single BASTImport at root level" in {
      val domain = mkDomain("Imported")
      val bi = mkBASTImport(domain)
      val root = mkRoot(bi)

      root.flatten()

      assert(root.contents.toSeq.size == 1)
      assert(root.contents.toSeq.head.isInstanceOf[Domain])
      assert(root.domains.head.id.value == "Imported")
    }

    "flatten nested includes (include within include)" in {
      val ctx = mkContext("Inner")
      val innerInclude = Include[Context](At(), contents = Contents(ctx))
      val domain = Domain(At(), mkId("Outer"),
        Contents[DomainContents](innerInclude.asInstanceOf[DomainContents]))
      val root = mkRoot(domain)

      root.flatten()

      assert(root.domains.size == 1)
      val d = root.domains.head
      assert(d.contexts.size == 1)
      assert(d.contexts.head.id.value == "Inner")
    }

    "flatten mixed Include and BASTImport nodes" in {
      val d1 = mkDomain("FromInclude")
      val d2 = mkDomain("FromImport")
      val include = Include[Domain](At(), contents = Contents(d1))
      val bi = mkBASTImport(d2)
      val root = mkRoot(include, bi)

      root.flatten()

      assert(root.domains.size == 2)
      assert(root.domains.map(_.id.value) == Seq("FromInclude", "FromImport"))
    }

    "preserve ordering after flattening" in {
      val d1 = mkDomain("First")
      val d2 = mkDomain("Second")
      val d3 = mkDomain("Third")
      val include = Include[Domain](At(), contents = Contents(d2))
      val root = mkRoot(d1, include, d3)

      root.flatten()

      assert(root.domains.size == 3)
      assert(root.domains.map(_.id.value) == Seq("First", "Second", "Third"))
    }

    "leave direct children unaffected" in {
      val d1 = mkDomain("Direct")
      val d2 = mkDomain("AlsoDirect")
      val root = mkRoot(d1, d2)

      root.flatten()

      assert(root.domains.size == 2)
      assert(root.domains.map(_.id.value) == Seq("Direct", "AlsoDirect"))
    }

    "flatten deep nesting: Domain > Include > Context > Include > Entity" in {
      val entity = mkEntity("DeepEntity")
      val innerInclude = Include[Entity](At(), contents = Contents(entity))
      val ctx = Context(At(), mkId("Mid"),
        Contents[ContextContents](innerInclude.asInstanceOf[ContextContents]))
      val outerInclude = Include[Context](At(), contents = Contents(ctx))
      val domain = Domain(At(), mkId("Top"),
        Contents[DomainContents](outerInclude.asInstanceOf[DomainContents]))
      val root = mkRoot(domain)

      root.flatten()

      val d = root.domains.head
      assert(d.id.value == "Top")
      assert(d.contexts.size == 1)
      val c = d.contexts.head
      assert(c.id.value == "Mid")
      assert(c.entities.size == 1)
      assert(c.entities.head.id.value == "DeepEntity")
    }

    "remove empty Include/BASTImport nodes cleanly" in {
      val domain = mkDomain("Real")
      val emptyInclude = Include[Domain](At(), contents = Contents.empty[Domain]())
      val emptyImport = BASTImport(
        loc = At(),
        path = LiteralString(At(), "empty.bast")
      )
      val root = mkRoot(emptyInclude, domain, emptyImport)

      root.flatten()

      assert(root.contents.toSeq.size == 1)
      assert(root.domains.head.id.value == "Real")
    }

    "make domain.contexts return all contexts after flattening" in {
      val c1 = mkContext("DirectCtx")
      val c2 = mkContext("IncludedCtx")
      val include = Include[Context](At(), contents = Contents(c2))
      val domain = Domain(At(), mkId("D"),
        Contents[DomainContents](
          c1.asInstanceOf[DomainContents],
          include.asInstanceOf[DomainContents]
        ))

      // Before flattening, only direct child is visible
      assert(domain.contexts.size == 1)
      assert(domain.contexts.head.id.value == "DirectCtx")

      domain.flatten()

      // After flattening, both are visible
      assert(domain.contexts.size == 2)
      assert(domain.contexts.map(_.id.value) == Seq("DirectCtx", "IncludedCtx"))
    }

    "work on Nebula (not just Root)" in {
      // Nebula contains definitions directly; put an Include
      // inside a Domain to verify recursion through Nebula
      val ctx = mkContext("NebCtx")
      val include = Include[Context](At(), contents = Contents(ctx))
      val domain = Domain(At(), mkId("NebDomain"),
        Contents[DomainContents](include.asInstanceOf[DomainContents]))
      val nebula = Nebula(At(), Contents[NebulaContents](domain))

      nebula.flatten()

      assert(nebula.contents.toSeq.size == 1)
      val d = nebula.contents.toSeq.head.asInstanceOf[Domain]
      assert(d.contexts.size == 1)
      assert(d.contexts.head.id.value == "NebCtx")
    }
  }
}
