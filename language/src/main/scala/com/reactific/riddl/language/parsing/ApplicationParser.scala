package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import fastparse.*
import fastparse.ScalaWhitespace.*

trait ApplicationParser
    extends CommonParser with ReferenceParser with TypeParser {

  def applicationOptions[u: P]: P[Seq[ApplicationOption]] = {
    options[u, ApplicationOption](StringIn(Options.technology).!) {
      case (loc, Options.technology, args) =>
        ApplicationTechnologyOption(loc, args)
      case (_, _, _) => throw new RuntimeException("Impossible case")
    }
  }

  def display[u: P]: P[Display] = {
    P(
      location ~ Keywords.display ~/ identifier ~ is ~ open ~ types ~
        Keywords.presents ~ typeRef ~ close ~ briefly ~ description
    ).map { case (loc, id, types, present, brief, description) =>
      Display(loc, id, types, present, brief, description)
    }
  }

  def form[u: P]: P[Form] = {
    P(
      location ~ Keywords.form ~/ identifier ~ is ~ open ~ types ~
        Keywords.presents ~ typeRef ~ Keywords.collects ~ typeRef ~ close ~
        briefly ~ description
    ).map { case (loc, id, types, present, collect, brief, description) =>
      Form(loc, id, types, present, collect, brief, description)
    }

  }

  def applicationDefinition[u: P]: P[ApplicationDefinition] = {
    P(display | form | author | term | typeDef | applicationInclude)
  }

  def applicationDefinitions[u: P]: P[Seq[ApplicationDefinition]] = {
    P(applicationDefinition.rep(0))
  }

  def applicationInclude[u: P]: P[Include[ApplicationDefinition]] = {
    include[ApplicationDefinition, u](applicationDefinitions(_))
  }

  def emptyApplication[
    u: P
  ]: P[(Seq[ApplicationOption], Seq[ApplicationDefinition])] = {
    undefined((Seq.empty[ApplicationOption], Seq.empty[ApplicationDefinition]))
  }

  def application[u: P]: P[Application] = {
    P(
      location ~ Keywords.application ~/ identifier ~ is ~ open ~
        (emptyApplication | (applicationOptions ~ applicationDefinitions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, (options, content), brief, desc) =>
      val groups = content.groupBy(_.getClass)
      val authors = mapTo[Author](groups.get(classOf[Author]))
      val types = mapTo[Type](groups.get(classOf[Type]))
      val displays = mapTo[Display](groups.get(classOf[Display]))
      val forms = mapTo[Form](groups.get(classOf[Form]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[ApplicationDefinition]](groups.get(
        classOf[Include[ApplicationDefinition]]
      ))

      Application(
        loc,
        id,
        options,
        types,
        displays,
        forms,
        authors,
        terms,
        includes,
        brief,
        desc
      )

    }
  }
}
