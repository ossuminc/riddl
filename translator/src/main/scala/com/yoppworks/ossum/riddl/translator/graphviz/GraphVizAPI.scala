package com.yoppworks.ossum.riddl.translator.graphviz

import java.nio.file.Path
import java.io._
import java.nio.charset.Charset

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import pureconfig._
import pureconfig.generic.auto._

/** GraphVizAPI
  * ##############################################################
  * #                    Linux Configurations                    #
  * ##############################################################
  * # The dir. where temporary files will be created.
  * tempDirForLinux = /tmp
  * # Where is your dot program located? It will be called externally.
  * dotForLinux = /usr/bin/dot
  *
  * ##############################################################
  * #                   Windows Configurations                   #
  * ##############################################################
  * # The dir. where temporary files will be created.
  * tempDirForWindows = c:/temp
  * # Where is your dot program located? It will be called externally.
  * dotForWindows = "c:/Program Files (x86)/Graphviz 2.28/bin/dot.exe"
  * */
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

case class GraphVizAPIConfig(
  tempDir: Path = Path.of("/tmp"),
  dotPath: Path = Path.of("/usr/local/bin"),
  imageDPI: Int = 96,
  dotProgramName: DotProgramName = dot,
  outputType: OutputKinds = svg
)

object GraphVizAPI {
  val tempDirForLinux = "/tmp"
  val dotForLinux = "/usr/local/bin/dot"

  /**
    * Detects the client's operating system.
    */
  final val osName: String = {
    System.getProperty("os.name").replaceAll("\\s", "")
  }

  final val configResourceName: String = "graphviz.conf"

  def getConfiguration: ConfigReader.Result[GraphVizAPIConfig] = {
    ConfigSource.default.load[GraphVizAPIConfig] match {
      case Left(_) =>
        ConfigSource.resources(configResourceName).load[GraphVizAPIConfig]
      case Right(success) =>
        Right(success)
    }
  }

  def apply(
    tempDir: Path = Path.of(tempDirForLinux),
    dotPath: Path = Path.of(dotForLinux)
  ): GraphVizAPI = {
    val config = getConfiguration match {
      case Left(failures) =>
        throw new RuntimeException(failures.prettyPrint())
      case Right(config) =>
        config
    }
    new GraphVizAPI(config)
  }
}

case class GraphVizAPI(config: GraphVizAPIConfig) {

  /**
    * The source of the graph written in dot language.
    */
  private final val graph: StringBuilder = new StringBuilder()

  /**
    * Returns the graph's source description in dot language.
    * @return Source of the graph in dot language.
    */
  def getDotSource: String = this.graph.toString()

  /** Adds a string to the graph's source (without newline). */
  def add(line: String): GraphVizAPI = {
    graph.append(line)
    this
  }

  /** Adds a string to the graph's source (with newline).  */
  def addln(line: String): GraphVizAPI = {
    graph.append(line + "\n")
    this
  }

  /** Adds a newline to the graph's source. */
  def addln: GraphVizAPI = {
    graph.append('\n')
    this
  }

  /** Resets the graph to empty */
  def clearGraph() = { graph.clear() }

  def runDot(implicit ec: ExecutionContext): Future[(Int, String, String)] = {
    import scala.sys.process._
    import scala.sys.process.ProcessBuilder._
    import java.io.File

    val program =
      config.dotPath.resolve(config.dotProgramName.toString).toString
    val command = program + s" -T${config.outputType} -Gdpi=${config.imageDPI}"

    val dotInput = getDotSource.getBytes(Charset.forName("UTF-8"))
    val input = new ByteArrayInputStream(dotInput)
    val stdout = new StringBuilder
    val stderr = new StringBuilder
    val logger = ProcessLogger(stdout append _, stderr append _)

    Future {
      Process(command) #< (input) ! logger match {
        case 0 =>
          (0, stdout.toString(), "")
        case errCode: Int =>
          (errCode, "", stderr.toString())
      }
    }
  }
  /*
  /**
 * Returns the graph as an image in binary format.
 * @param dot_source Source of the graph to be drawn.
 * @param graphType Type of the output image to be produced, e.g.:
 *                  gif, dot, fig, pdf, ps, svg, png.
 * @return A byte array containing the image of the graph.
 */
  def getGraph(dot_source: String, graphType: String): Array[Byte] = {
    File dot;
    byte[] img_stream = null;

    try {
      dot = writeDotSourceToFile(dot_source);
      if (dot != null)
      {
        img_stream = get_img_stream(dot, type);
        if (dot.delete() == false)
          System.err.println("Warning: " + dot.getAbsolutePath() + " could not be deleted!");
        return img_stream;
      }
      return null;
    } catch (java.io.IOException ioe) { return null; }
  }

  /**
 * Writes the graph's image in a file.
 * @param img   A byte array containing the image of the graph.
 * @param file  Name of the file to where we want to write.
 * @return Success: 1, Failure: -1
 */
  public int writeGraphToFile(byte[] img, String file)
  {
    File to = new File(file);
    return writeGraphToFile(img, to);
  }

  /**
 * Writes the graph's image in a file.
 * @param img   A byte array containing the image of the graph.
 * @param to    A File object to where we want to write.
 * @return Success: 1, Failure: -1
 */
  public int writeGraphToFile(byte[] img, File to)
  {
    try {
      FileOutputStream fos = new FileOutputStream(to);
      fos.write(img);
      fos.close();
    } catch (java.io.IOException ioe) { return -1; }
    return 1;
  }

  /**
 * It will call the external dot program, and return the image in
 * binary format.
 * @param dot Source of the graph (in dot language).
 * @param type Type of the output image to be produced, e.g.: gif, dot, fig, pdf, ps, svg, png.
 * @return The image of the graph in .gif format.
 */
  private byte[] get_img_stream(File dot, String type)
  {
    File img;
    byte[] img_stream = null;

    try {
      img = File.createTempFile("graph_", "."+type, new File(GraphViz.TEMP_DIR));
      Runtime rt = Runtime.getRuntime();

      // patch by Mike Chenault
      String[] args = {DOT, "-T"+type, "-Gdpi="+dpiSizes[this.currentDpiPos], dot.getAbsolutePath(), "-o", img.getAbsolutePath()};
      Process p = rt.exec(args);

      p.waitFor();

      FileInputStream in = new FileInputStream(img.getAbsolutePath());
      img_stream = new byte[in.available()];
      in.read(img_stream);
      // Close it if we need to
      if( in != null ) in.close();

      if (img.delete() == false)
        System.err.println("Warning: " + img.getAbsolutePath() + " could not be deleted!");
    }
    catch (java.io.IOException ioe) {
      System.err.println("Error:    in I/O processing of tempfile in dir " + GraphViz.TEMP_DIR+"\n");
      System.err.println("       or in calling external command");
      ioe.printStackTrace();
    }
    catch (java.lang.InterruptedException ie) {
    System.err.println("Error: the execution of the external program was interrupted");
    ie.printStackTrace();
  }

    return img_stream;
  }

  /**
 * Writes the source of the graph in a file, and returns the written file
 * as a File object.
 * @param str Source of the graph (in dot language).
 * @return The file (as a File object) that contains the source of the graph.
 */
  private File writeDotSourceToFile(String str) throws java.io.IOException
{
  File temp;
  try {
    temp = File.createTempFile("dorrr",".dot", new File(GraphViz.TEMP_DIR));
    FileWriter fout = new FileWriter(temp);
    fout.write(str);
    BufferedWriter br=new BufferedWriter(new FileWriter("dotsource.dot"));
    br.write(str);
    br.flush();
    br.close();
    fout.close();
  }
  catch (Exception e) {
    System.err.println("Error: I/O error while writing the dot source to temp file!");
    return null;
  }
  return temp;
}

  /**
 * Returns a string that is used to start a graph.
 * @return A string to open a graph.
 */
  public String start_graph() {
    return "digraph G {";
  }

  /**
 * Returns a string that is used to end a graph.
 * @return A string to close a graph.
 */
  public String end_graph() {
    return "}";
  }

  /**
 * Takes the cluster or subgraph id as input parameter and returns a string
 * that is used to start a subgraph.
 * @return A string to open a subgraph.
 */
  public String start_subgraph(int clusterid) {
    return "subgraph cluster_" + clusterid + " {";
  }

  /**
 * Returns a string that is used to end a graph.
 * @return A string to close a graph.
 */
  public String end_subgraph() {
    return "}";
  }

  /**
 * Read a DOT graph from a text file.
 *
 * @param input Input text file containing the DOT graph
 * source.
 */
  public void readSource(String input)
  {
    StringBuilder sb = new StringBuilder();

    try
      {
        FileInputStream fis = new FileInputStream(input);
        DataInputStream dis = new DataInputStream(fis);
        BufferedReader br = new BufferedReader(new InputStreamReader(dis));
        String line;
        while ((line = br.readLine()) != null) {
          sb.append(line);
        }
        dis.close();
      }
    catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
    }

    this.graph = sb;
  }
 */
} // end of class GraphViz
