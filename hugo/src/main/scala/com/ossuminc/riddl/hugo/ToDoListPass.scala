package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.{CollectingPass, CollectingPassOutput, PassInfo, PassInput, PassesOutput}

import scala.collection.mutable

case class ToDoItem(
  item: String,
  author: String,
  path: String,
  link: String
)

case class ToDoListOutput(
  messages: Messages.Messages,
  map: Map[String, Seq[ToDoItem]],
  collected: Seq[ToDoItem] = Seq.empty
) extends CollectingPassOutput[ToDoItem]

case class ToDoListPass(input: PassInput, outputs: PassesOutput, options: HugoCommand.Options)
    extends CollectingPass[ToDoItem](input, outputs)
    with PassUtilities {

  protected def collect(definition: Definition, parents: mutable.Stack[Definition]): Option[ToDoItem] = {
    val pars = parents.toSeq
    val item = definition.identify
    val authors = AST.findAuthors(definition, pars)
    val author = mkAuthor(authors, pars)
    val prnts = makeStringParents(pars)
    val path = prnts.mkString(".")
    val link = makeDocLink(definition, prnts)
    Some(ToDoItem(item, author, path, link))
  }

  @SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
  private def mkAuthor(authors: Seq[AuthorRef], parents: Seq[Definition]): String = {
    if authors.isEmpty then "Unspecified Author"
    else
      parents.headOption match {
        case None => "Unspecified Author"
        case Some(parent: Definition) =>
          authors
            .map { (ref: AuthorRef) =>
              outputs.refMap.definitionOf[Author](ref.pathId, parent)
            }
            .filterNot(_.isEmpty)
            .map(_.get)
            .map(x => s"${x.name.s} &lt;${x.email.s}&gt;")
            .mkString(", ")
      }
  }

  override def result: ToDoListOutput = {
    val map: Map[String, Seq[ToDoItem]] =
      collectedValues.groupBy(_._2).view.toMap
    ToDoListOutput(messages.toMessages, map)
  }
  def name: String = ToDoListPass.name
  def postProcess(root: RootContainer): Unit = ()
}

object ToDoListPass extends PassInfo {
  val name: String = "ToDoList"
}

// val finder: Finder = Finder(root)
//       val items: Seq[(String, String, String, String)] = {
//         for {
//           (defn: Definition, pars: Seq[Definition]) <- finder.findEmpty
//           item = defn.identify
//           authors = AST.findAuthors(defn, pars)
//           author = mkAuthor(authors, pars)
//           parents = makeParents(pars)
//           path = parents.mkString(".")
//           link = makeDocLink(defn, parents)
//         } yield (item, author, path, link)
//       }
//
//       val map = items
//         .groupBy(_._2)
//         .view
//         .mapValues(_.map { case (item, _, path, link) =>
//           s"[$item In $path]($link)"
//         })
//         .toMap
