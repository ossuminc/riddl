package com.yoppworks.ossume.riddl.generator.d3

import com.yoppworks.ossum.riddl.language.AST.{Container, Definition, RootContainer}
import com.yoppworks.ossum.riddl.language.{AST, Folding}
import ujson.*

import java.net.URL
import scala.collection.mutable

case class TableOfContents(
  baseURL: URL,
  rootContainer: RootContainer
) {

  private val base = baseURL.toString

  case class Entry(name: String, children: Seq[Entry], link: String)

  type ParentStack = mutable.Stack[Container[Definition]]
  private val empty = mutable.Stack.empty[Container[Definition]]

  private def mkPathId(definition: Definition, stack: ParentStack): String = {
    val start = new StringBuilder(base)
    val sb = stack.foldRight(start) { (definition, sb) =>
      definition match {
        case _: RootContainer => sb
        case d: Definition => sb.append(d.id.format).append("/")
      }
    }
    sb.append(definition.id.format).toString
  }

  private def mkObject(d: Definition, name: String, stack: ParentStack): Obj = {
    Obj("name" -> name, "link" -> mkPathId(d, stack), "children" -> Arr())
  }

  private def addDef(entry: Obj, d: Definition, stack: ParentStack): Obj = {
    val child = mkObject(d, s"${AST.kind(d)}:${d.id.format}", stack)
    entry.obj("children").arr.append(child)
    child
  }

  def makeData: Arr = {
    val rootObj = mkObject(rootContainer, "root", empty)

    Folding.foldLeft(rootObj, empty)(rootContainer) { (entry, definition, stack) =>
      definition match {
        case _: RootContainer =>
          entry
        case c: Container[?] =>
          addDef(entry, c, stack)
        case d: Definition =>
          addDef(entry, d, stack)
          entry
        case _ =>
          entry
      }
    }
    rootObj("children").asInstanceOf[Arr]
  }
}
