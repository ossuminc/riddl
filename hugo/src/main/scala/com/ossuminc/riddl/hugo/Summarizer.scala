package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.language.AST.{Domain, Root}
import com.ossuminc.riddl.utils.Timer
import diagrams.mermaid.RootOverviewDiagram

trait Summarizer { this: HugoPass =>

  def summarize(): Unit = {
    makeIndex(root)
    makeGlossary()
    makeToDoList()
    makeStatistics()

    // TODO: makeUsers()
    if options.withMessageSummary then for d <- root.domains do makeMessageSummary(d)
  }

  private def makeIndex(root: Root): Unit = {
    Timer.time("Index Creation") {

      val mdw = this.makeWriter(Seq.empty[String], "_index.md")
      mdw.fileHead("Index", 10, Option("The main index to the content"))
      mdw.h2("Root Overview")
      val diagram = RootOverviewDiagram(root)
      mdw.emitMermaidDiagram(diagram.generate)
      mdw.h2("Domains")
      val domains = root.domains
        .sortBy(_.id.value)
        .map(d => s"[${d.id.value}](${d.id.value.toLowerCase}/)")
      mdw.list(domains)
      mdw.h2("Indices")
      val glossary =
        if options.withGlossary then {
          Seq("[Glossary](glossary)")
        } else {
          Seq.empty[String]
        }
      val todoList = {
        if options.withTODOList then {
          Seq("[To Do List](todolist)")
        } else {
          Seq.empty[String]
        }
      }
      val statistics = {
        if options.withStatistics then {
          Seq("[Statistics](statistics)")
        } else {
          Seq.empty[String]
        }
      }
      mdw.list(glossary ++ todoList ++ statistics)
    }
  }

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

  private def makeMessageSummary(domain: Domain): Unit = {
    Timer.time(s"Messages Summary for ${domain.identify}") {
      val fileName = s"${domain.id.value}-messages.md"
      val mdw = makeWriter(Seq(domain.id.value), fileName)
      mdw.fileHead(
        s"${domain.identify} Message Summary",
        25,
        Some(s"Message Summary for ${domain.identify}")
      )
      outputs.outputOf[MessageOutput](MessagesPass.name) match {
        case Some(mo) =>
          val infos = mo.collected.filter(_.link.contains(domain.id.value.toLowerCase))
          mdw.emitMessageSummary(domain, infos)
        case None =>
          mdw.emitMessageSummary(domain, Seq.empty)

      }
    }
  }
}
