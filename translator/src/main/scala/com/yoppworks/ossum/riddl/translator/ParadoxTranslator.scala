package com.yoppworks.ossum.riddl.translator

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

import com.yoppworks.ossum.riddl.language.AST._
import com.yoppworks.ossum.riddl.language.Folding
import com.yoppworks.ossum.riddl.language.Riddl
import com.yoppworks.ossum.riddl.language.Translator
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import pureconfig.generic.auto._

/** A Translator that generates Paradox documentation */
class ParadoxTranslator extends Translator {

  case class ParadoxConfig(
    showTimes: Boolean = false,
    showWarnings: Boolean = true,
    showMissingWarnings: Boolean = false,
    showStyleWarnings: Boolean = false,
    inputPath: Option[Path] = None,
    outputRoot: Path = Path.of("target")
  ) extends Configuration

  type CONF = ParadoxConfig

  val defaultConfig = ParadoxConfig()

  override def loadConfig(path: Path): ConfigReader.Result[ParadoxConfig] = {
    ConfigSource.file(path).load[ParadoxConfig]
  }

  case class ParadoxState(
    config: ParadoxConfig,
    generatedFiles: Seq[File] = Seq.empty[File]
  ) extends Folding.State[ParadoxState]
      with State {
    def step(f: ParadoxState => ParadoxState): ParadoxState = f(this)

    def addFile(f: File): ParadoxState = {
      this.copy(generatedFiles = this.generatedFiles :+ f)
    }
  }

  def translate(
    root: RootContainer,
    logger: Riddl.Logger,
    config: CONF
  ): Seq[File] = {
    val state: ParadoxState = ParadoxState(config, Seq.empty[File])
    val dispatcher = ParadoxFolding(root, root, state)
    dispatcher.foldLeft(root, root, state).generatedFiles
  }

  case class ParadoxFolding(
    container: Container,
    definition: Definition,
    state: ParadoxState
  ) extends Folding.Folding[ParadoxState] {

    def mkDefRef(definition: Definition): String = {
      s"""* [${definition.id.value}]("${definition.id.value}.md")"""
    }

    def mkContRef(cont: Container): String = {
      s"""* [${cont.id.value}]("${cont.id.value.toLowerCase()}/index.md")"""
    }

    def mkFields(
      section: String,
      fields: Map[Identifier, Seq[LiteralString]]
    ): String = {
      val sb = new StringBuilder
      sb.append(s"## $section")
      fields.foreach {
        case (id, strs) =>
          sb.append(s"### ${id.value}")
          sb.append(strs.map(_.s).mkString("\n"))
      }
      sb.toString()
    }

    override def openDomain(
      state: ParadoxState,
      container: Container,
      domain: Domain
    ): ParadoxState = {
      val baseDir = state.config.outputRoot
        .resolve("riddl-paradox")
        .resolve(domain.id.value)
      Files.createDirectories(baseDir)
      val indexFile = baseDir.resolve("index.md")
      val w = new PrintWriter(indexFile.toFile)
      val d = domain.description.getOrElse(Description())
      w.write(
        s"""
           |# Domain `${domain.id.value}`
           |## Briefly
           |${d.brief.map(_.s).mkString("\n")}
           |
           |## Details
           |${d.details.map(_.s).mkString("\n")}
           |
           |${mkFields("Fields", d.fields)}
           |
           |@@toc { depth=2 }
           |
           |@@@ index
           |
           |${domain.types.map(mkDefRef).mkString("\n")}
           |${domain.contexts.map(mkContRef).mkString("\n")}
           |${domain.interactions.map(mkContRef).mkString("\n")}
           |
           |@@@
           |
           |""".stripMargin
      )
      w.flush()
      w.close()
      state
    }

    override def doType(
      state: ParadoxState,
      container: Container,
      typ: Type
    ): ParadoxState = {
      super.doType(state, container, typ)
    }
  }
}
