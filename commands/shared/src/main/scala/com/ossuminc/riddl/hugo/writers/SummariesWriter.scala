/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.{GlossaryEntry, MessageInfo, ToDoItem}
import com.ossuminc.riddl.language.AST.{Domain, User, Author}
import com.ossuminc.riddl.passes.stats.{KindStats, StatsOutput, StatsPass}

trait SummariesWriter { this: MarkdownWriter =>

  def emitStatistics(weight: Int): Unit = {
    fileHead(
      "Model Statistics",
      weight,
      Some("Statistical information about the RIDDL model documented")
    )

    val stats = generator.outputs.outputOf[StatsOutput](StatsPass.name).getOrElse(StatsOutput())
    emitTableHead(
      Seq(
        "Category" -> 'L',
        "count" -> 'R',
        "% of All" -> 'R',
        "% documented" -> 'R',
        "number empty" -> 'R',
        "avg completeness" -> 'R',
        "avg complexity" -> 'R',
        "avg containment" -> 'R'
      )
    )
    val total_stats: KindStats = stats.categories.getOrElse("All", KindStats())
    stats.categories.foreach { case (key, s) =>
      emitTableRow(
        key,
        s.count.toString,
        f"%%1.2f".format(s.percent_of_all(total_stats.count)),
        f"%%1.2f".format(s.percent_documented),
        s.numEmpty.toString,
        f"%%1.3f".format(s.completeness),
        f"%%1.3f".format(s.complexity),
        f"%%1.3f".format(s.averageContainment)
      )
    }
  }

  private def makeIconLink(id: String, title: String, link: String): String = {
    if link.nonEmpty then {
      s"[{{< icon \"$id\" >}}]($link \"$title\")"
    } else {
      ""
    }
  }

  private def emitTermRow(entry: GlossaryEntry): Unit = {
    val source_link = makeIconLink("gdoc_github", "Source Link", entry.sourceLink)
    val term = s"[${mono(entry.term)}](${entry.link})$source_link"
    val concept_link =
      s"<small>[${entry.kind.toLowerCase}](https://riddl.tech/concepts/${entry.kind.toLowerCase}/)</small>"
    emitTableRow(term, concept_link, entry.brief)
  }

  def emitGlossary(
    weight: Int,
    terms: Seq[GlossaryEntry]
  ): Unit = {
    fileHead("Glossary Of Terms", weight, Some("A generated glossary of terms"))

    emitTableHead(Seq("Term" -> 'C', "Type" -> 'C', "Brief Description" -> 'L'))

    terms.sortBy(_.term).foreach { entry => emitTermRow(entry) }
  }

  def emitToDoList(weight: Int, items: Seq[ToDoItem]): Unit = {
    fileHead(
      "To Do List",
      weight,
      Option("A list of definitions needing more work")
    )
    h2("Definitions With Missing Content")
    for { (author, info) <- items.groupBy(_.author) } do {
      h3(author)
      emitTableHead(
        Seq(
          "Item Name" -> 'C',
          "Path To Item" -> 'C'
        )
      )
      for { item <- info.map { item => item.item -> s"[${item.path}](${item.link})" } } do
        emitTableRow(item._1, item._2)
    }
  }
  
  def emitUsers(weight: Int, users: Seq[User]): Unit = {
    fileHead("Users", weight, Some("A list of the users defined for epics"))
    h2("Users")
    for { user <- users } do 
      h3(user.identify)
      list(
        Seq(
          s"Is a: ${user.is_a.format}",
          s"Brief: ${user.briefString}"
        )
      )
      user.descriptionString
  }
  
  def emitAuthors(weight: Int, authors: Seq[Author]): Unit = {
    fileHead("Authors", weight, Some("A list of the authors of this specification model"))
    h2("Authors")
    for {author <- authors} do
      h3(author.id.format)
      list(
        Seq(
          s"Name: ${author.name.format}",
          s"Title: ${author.title.format}",
          s"Organization: ${author.organization.format}",
          s"URL: ${author.url.map(_.toString)}",
          s"Email: ${author.email.format}",
          s"Brief: ${author.briefString}"
        )
      )
      author.descriptionString
  }

  def emitMessageSummary(domain: Domain, messages: Seq[MessageInfo], kind: String): Unit = {
    h3(kind + " Messages")
    emitTableHead(
      Seq(
        "Name" -> 'C',
        "Users" -> 'C',
        "Description" -> 'L'
      )
    )

    for {
      message <- messages
    } do {
      emitTableRow(
        message.message,
        message.users,
        message.description
      )
    }
  }

}
