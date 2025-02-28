/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands.hugo

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{Context, Domain, Entity, Parents, Root}
import com.ossuminc.riddl.passes.symbols.Symbols
import com.ossuminc.riddl.utils.{PlatformContext, Timer}
import com.ossuminc.riddl.diagrams.mermaid.RootOverviewDiagram
import com.ossuminc.riddl.language.AST.Author

import scala.reflect.ClassTag

trait Summarizer(using PlatformContext) {
  this: HugoPass =>

  def summarize(): Unit = {
    makeIndex()
    makeStatistics()
    makeGlossary()
    makeUsers()
    makeAuthors()
    makeToDoList()
  }

  private def makeIndex(): Unit = {
    Timer.time("Index Creation") {

      val mdw = this.makeWriter(Seq.empty[String], "_index.md")
      mdw.fileHead("Index", 10, Option("The main index to the content"))
      mdw.h2("Root Overview")
      root match
        case r: Root =>
          val diagram = RootOverviewDiagram(r)
          diagram.generate
          mdw.emitMermaidDiagram(diagram.generate)
        case _ => 
      end match     
      mdw.h2("Index")
      val authors = Seq("[Authors](authors)")
      val users = Seq("[Users](users)")
      val glossary = if options.withGlossary then Seq("[Glossary](glossary)") else Seq.empty[String]
      val todoList = if options.withTODOList then Seq("[To Do List](todolist)") else Seq.empty[String]
      val statistics = if options.withStatistics then Seq("[Statistics](statistics)") else Seq.empty[String]
      val messageSummary =
        if options.withMessageSummary then Seq("[Message Summary](messages-summary)") else Seq.empty[String]
      mdw.list(authors ++ users ++ glossary ++ todoList ++ statistics ++ messageSummary)
      mdw.makeRootIndex(root)
    }
  }

  private val authorsWeight = 950
  private val usersWeight = 960
  private val glossaryWeight = 970
  private val toDoWeight = 980
  private val statsWeight = 990

  private def makeStatistics(): Unit = {
    if options.withStatistics then {
      Timer.time("Make Statistics") {
        val mdw = makeWriter(Seq.empty[String], fileName = "statistics.md")
        mdw.emitStatistics(statsWeight)
      }
    }
  }

  private def makeGlossary(): Unit = {
    if options.withGlossary then {
      Timer.time("Make Glossary") {
        val mdw = makeWriter(Seq.empty[String], "glossary.md")
        outputs.outputOf[GlossaryOutput](GlossaryPass.name) match {
          case Some(go) =>
            mdw.emitGlossary(glossaryWeight, go.entries)
          case None =>
            mdw.emitGlossary(glossaryWeight, Seq.empty)
        }
      }
    }
  }

  private def makeToDoList(): Unit = {
    if options.withTODOList then
      Timer.time("Make ToDo List") {
        outputs.outputOf[ToDoListOutput](ToDoListPass.name) match {
          case Some(output) =>
            val mdw = makeWriter(Seq.empty[String], "todolist.md")
            mdw.emitToDoList(toDoWeight, output.collected)
          case None =>
          // do nothing
        }
      }
  }

  private def makeUsers(): Unit = {
    root match
      case root1: Root =>
        val users = AST.getUsers(root1)
        val mdw = makeWriter(Seq.empty[String], fileName = "users.md")
        mdw.emitUsers(usersWeight, users)
      case _ =>
    end match   
  }

  private def makeAuthors(): Unit = {
    root match
      case root1: Root =>
        val authors = root1.contents.filter[Author] ++ AST.getAuthors(root1)
        val mdw = makeWriter(Seq.empty[String], fileName = "authors.md")
        mdw.emitAuthors(authorsWeight, authors)
      case _ =>
    end match    
  }

  protected def makeMessageSummary(parents: Parents, domain: Domain): Unit = {
    if options.withMessageSummary then
      val fileName = s"messages-summary.md"
      val stringParents = generator.makeStringParents(parents)
      val mdw = makeWriter(stringParents, fileName)
      mdw.fileHead(s"Message Summary", 25, Some(s"Message Summary for ${domain.identify}"))
      outputs.outputOf[MessageOutput](MessagesPass.name) match {
        case Some(mo) =>
          val map = mo.collected.filter(_.link.contains(domain.id.value.toLowerCase)).groupBy(_.kind)
          for { (kind, infos) <- map } do {
            mdw.emitMessageSummary(domain, infos, kind)
          }
        case None =>
          mdw.p(s"No message definitions in ${domain.identify}")
      }
  }
}
