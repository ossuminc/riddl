package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.{Domain, Root, Context, Entity, Parents}
import com.ossuminc.riddl.passes.symbols.Symbols
import com.ossuminc.riddl.utils.Timer
import com.ossuminc.riddl.diagrams.mermaid.RootOverviewDiagram

import scala.reflect.ClassTag

trait Summarizer {
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
      val diagram = RootOverviewDiagram(root)
      mdw.emitMermaidDiagram(diagram.generate)
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
    val users = AST.getUsers(root)
    val mdw = makeWriter(Seq.empty[String], fileName = "users.md")
    mdw.emitUsers(usersWeight, users)
  }

  private def makeAuthors(): Unit = {
    val authors = root.authors ++ AST.getAuthors(root)
    val mdw = makeWriter(Seq.empty[String], fileName = "authors.md")
    mdw.emitAuthors(authorsWeight, authors)
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
