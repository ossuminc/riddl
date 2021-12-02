package com.yoppworks.ossum.riddl.translator.graph

import java.nio.file.Path
import java.io._
import java.nio.charset.Charset

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import pureconfig._
import pureconfig.generic.auto._

/** GraphVizAPI ############################################################## #
  * Linux Configurations #
  * ############################################################## # The dir.
  * where temporary files will be created. tempDirForLinux = /tmp # Where is
  * your dot program located? It will be called externally. dotForLinux =
  * /usr/bin/dot
  *
  * ############################################################## # Windows
  * Configurations #
  * ############################################################## # The dir.
  * where temporary files will be created. tempDirForWindows = c:/temp # Where
  * is your dot program located? It will be called externally. dotForWindows =
  * "c:/Program Files (x86)/Graphviz 2.28/bin/dot.exe"
  */
sealed trait DotProgramName {
  override def toString: String = { this.getClass.getSimpleName.dropRight(1) }
}

// scalastyle:off
case object dot extends DotProgramName
case object neato extends DotProgramName
case object twopi extends DotProgramName
case object circo extends DotProgramName
case object fdp extends DotProgramName
case object sfdp extends DotProgramName
case object patchwork extends DotProgramName
case object osage extends DotProgramName

sealed trait OutputKinds {
  override def toString: String = { this.getClass.getSimpleName.dropRight(1) }
}

// Dot format containing layout info
case object dot_ extends OutputKinds {
  override def toString: String = "dot"
}
case object xdot extends OutputKinds // Dot format containing all layout info
case object ps extends OutputKinds // PostScript
case object pdf extends OutputKinds // Page Description Format
case object svg extends OutputKinds // Structured Vector Graphics
case object svgz extends OutputKinds // Compressed SVG
case object fig extends OutputKinds // XFIG graphics
case object png extends OutputKinds // png bitmap graphics
case object gif extends OutputKinds // gif bitmap graphics
case object jpg extends OutputKinds // JPEG bitmap graphics
case object jpeg extends OutputKinds // JPEG bitmap graphics
case object json extends OutputKinds // xdot information encoded in JSON
case object imapo extends OutputKinds
// imagemap files for httpd servers for
// each node  or edge that has a non-null href attribute.
case object cmapx extends OutputKinds
// client-side imagemap for use in html and xhtml
// scalastyle:on

case class GraphVizAPIConfig(
  tempDir: Path = Path.of("/tmp"),
  dotPath: Path = Path.of("/usr/bin"),
  imageDPI: Int = 96,
  dotProgramName: DotProgramName = dot,
  outputType: OutputKinds = svg)

object GraphVizAPI {

  /** Detects the client's operating system.
    */
  final val osName: String = {
    System.getProperty("os.name").replaceAll("\\s", "")
  }

  final val configResourceName: String = "graphviz.conf"

  def getConfiguration: ConfigReader.Result[GraphVizAPIConfig] = {
    ConfigSource.default.load[GraphVizAPIConfig] match {
      case Left(_) => ConfigSource.resources(configResourceName)
          .load[GraphVizAPIConfig]
      case Right(success) => Right(success)
    }
  }

  def apply(sb: StringBuilder): GraphVizAPI = {
    val config = getConfiguration match {
      case Left(failures) => throw new RuntimeException(failures.prettyPrint())
      case Right(config)  => config
    }
    new GraphVizAPI(config, sb)
  }

  @volatile
  private var clusterNum = 0

  implicit class StringBuilderExtras(sb: StringBuilder) {

    def nl: StringBuilder = { sb.append('\n') }

    private def fmtattr(a: (String, String)): String = { s"${a._1}=${a._2}" }

    def attrs(attributes: Seq[(String, String)]): StringBuilder = {
      sb.append(attributes.map(fmtattr).mkString(";\n")).append(";").nl
    }

    def node(attributes: Seq[(String, String)]): StringBuilder = {
      sb.append("node [").append(attributes.map(fmtattr).mkString(", "))
        .append("];").nl
    }

    def edge(attributes: Seq[(String, String)]): StringBuilder = {
      sb.append("edge [").append(attributes.map(fmtattr).mkString(", "))
        .append("];").nl
    }

    def node(name: String, attributes: Seq[(String, String)]): StringBuilder = {
      sb.append(name).append(" [")
        .append(attributes.map(fmtattr).mkString(", ")).append("];").nl
    }

    def relation(from: String, to: String): StringBuilder = {
      sb.append(s"$from -> $to ;").nl
    }

    def relation3(from: String, to: String, andThen: String): StringBuilder = {
      sb.append(s"$from -> $to -> $andThen ;").nl
    }

    def multiway(from: String, tos: String*): StringBuilder = {
      tos.foldLeft(sb) { (sb, to) => sb.append(s"$from -> $to ;").nl }
    }

    def subgraph(
      name: String
    )(content: StringBuilder => StringBuilder
    ): StringBuilder = {
      sb.append(s"subgraph $name {").nl
        .append(content(new StringBuilder).mkString.indent(2)).append("}").nl
    }

    def cluster(
      name: String
    )(content: StringBuilder => StringBuilder
    ): StringBuilder = {
      clusterNum = clusterNum + 1
      subgraph(s"cluster$clusterNum") { sb =>
        sb.attrs(Seq("label" -> name)).append(content(sb))
      }
    }

    def digraph(
      name: String
    )(content: StringBuilder => StringBuilder
    ): StringBuilder = {
      sb.append(s"digraph $name {").nl
        .append(content(new StringBuilder).mkString.indent(2)).append("}").nl
    }

    def expand[T](
      list: Seq[T]
    )(f: (StringBuilder, T) => StringBuilder
    ): StringBuilder = {
      list.foldLeft(new StringBuilder) { (sb, it) => f(sb, it) }
    }

    /** Adds a string to the graph's source (without newline). */
    def add(line: String): StringBuilder = { sb.append(line) }

    /** Adds a string to the graph's source (with newline). */
    def addln(line: String): StringBuilder = { sb.append(line + "\n") }

    /** Adds a newline to the graph's source. */
    def addln: StringBuilder = { sb.append('\n') }
  }
}

case class GraphVizAPI(config: GraphVizAPIConfig, buffer: StringBuilder) {

  /** Returns the graph's source description in dot language.
    * @return
    *   Source of the graph in dot language.
    */
  def getDotSource: String = this.buffer.toString()

  def runDot(implicit ec: ExecutionContext): Future[(Int, String, String)] = {
    import scala.sys.process._

    val program = config.dotPath.resolve(config.dotProgramName.toString)
      .toString
    val command = program + s" -T${config.outputType} -Gdpi=${config.imageDPI}"

    val dotInput = getDotSource.getBytes(Charset.forName("UTF-8"))
    val input = new ByteArrayInputStream(dotInput)
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val logger = ProcessLogger(stdout append _, stderr append _)

    Future {
      Process(command) #< input ! logger match {
        case 0            => (0, stdout.toString(), "")
        case errCode: Int => (errCode, "", stderr.toString())
      }
    }
  }
}
