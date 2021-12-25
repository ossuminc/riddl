package com.yoppworks.ossum.riddl.generation.hugo

import cats.Eval

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.matching.Regex
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.InvalidParameterException
import scala.Console.println
import scala.annotation.tailrec

object Resources extends SafeUsingCloseable {
  private val pathSep = System.getProperty("path.separator")
  private val classPath = System.getProperty("java.class.path", ".")
  private val resourceTemplateName = "template"
  private val jarPath = getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath
    .stripSuffix("/")
  private lazy val classPathItems = classPath.split(pathSep).toVector
  private lazy val ctxClassLoader = Thread.currentThread.getContextClassLoader

  def listResourcesInJar(
    pattern: String = "",
    jarOrPath: String = jarPath
  ): Try[Seq[ResourceEntry]] = {
    val matching =
      if (pattern.nonEmpty) { Some(new Regex(pattern)) }
      else { None }
    val searchItems = classPathItems.filter(_.contains(jarOrPath))
    sequenceTry(searchItems.map(getResourceEntries(_, matching))).map(_.flatten)
  }

  def copyResourcesTo(
    absoluteDestinationPath: String,
    resources: Seq[ResourceEntry]
  ): Try[Int] = for {
    outputRoot <- validateOutputDirectory(absoluteDestinationPath)
    directories = resources.collect { case d: DirectoryEntry => d }
    files = resources.collect { case f: FileEntry => f }
    dirsCnt <- sequenceTry(directories.map(createOutputDirectory(outputRoot, _))).map(_.size)
    filesCnt <- sequenceTry(files.map(writeOutputFile(outputRoot, _))).map(_.size)
  } yield dirsCnt + filesCnt

  private def getResourceEntries(
    pathItem: String,
    pattern: Option[Regex]
  ): Try[Seq[ResourceEntry]] = Try(new File(pathItem)).flatMap { file =>
    if (file.isDirectory) { FileUtils.getResourcesFromDirectory(file, pattern) }
    else { JarUtils.getResourcesFromJarFile(file, pattern) }
  }

  private def sequenceTry[A](in: Seq[Try[A]]): Try[Seq[A]] = {
    @tailrec
    def loop(items: Vector[Try[A]], acc: Vector[A]): Try[Vector[A]] = items match {
      case Failure(err) +: _   => Failure(err)
      case Success(ok) +: tail => loop(tail, acc :+ ok)
      case _ /* empty */       => Success(acc)
    }
    loop(in.toVector, Vector.empty)
  }

  private def validateOutputDirectory(absDestinationPath: String): Try[File] = Try {
    val outputDir = new File(absDestinationPath)
    if (outputDir.exists && !outputDir.isDirectory) {
      throw new InvalidParameterException(
        s"Output path must be a directory (`$absDestinationPath` is a file)"
      )
    } else if (!outputDir.exists) { outputDir.mkdirs() }
    outputDir
  }

  private def createOutputDirectory(
    output: File,
    entry: DirectoryEntry
  ): Try[Unit] = Try {
    val relDirName = replaceTemplateName(entry.nameOrPath)
    output.toPath.resolve(relDirName).toFile.mkdirs()
  }

  @inline
  private final def replaceTemplateName(nameOrPath: String): String =
    if (nameOrPath.isEmpty) { nameOrPath }
    else {
      val parts = nameOrPath.split('/')
      val updatedParts =
        if (parts(0) == resourceTemplateName) { parts.drop(1) }
        else { parts }
      updatedParts.mkString("/")
    }

  private final val copyBufferSize = 4096
  private def writeOutputFile(root: File, entry: FileEntry): Try[Unit] = Try {
    val relFileName = replaceTemplateName(entry.nameOrPath)
    val outputFile = root.toPath.resolve(relFileName).toFile
    // Simulate filesystem "touch"
    new FileOutputStream(outputFile).close()
    outputFile
  } flatMap { file =>
    usingTry(ctxClassLoader.getResourceAsStream(entry.nameOrPath)) { inputStream =>
      using(new FileOutputStream(file)) { outputStream =>
        val buffer = Array.ofDim[Byte](copyBufferSize)
        var read = inputStream.read(buffer)
        while (read > 0) {
          outputStream.write(buffer, 0, read)
          outputStream.flush()
          read = inputStream.read(buffer)
        }
      }
    }
  }
}

sealed trait ResourceEntry {
  def nameOrPath: String
}
final case class FileEntry(nameOrPath: String) extends ResourceEntry
final case class DirectoryEntry(nameOrPath: String) extends ResourceEntry

sealed private[hugo] trait SafeUsingCloseable {

  protected def using[A <: AutoCloseable, Out](
    allocate: => A
  )(using: A => Out
  ): Try[Out] = Try(allocate) flatMap { resource =>
    try Success(using(resource))
    catch { case error: Throwable => Failure(error) }
    finally resource.close()
  }

  //
  protected def usingTry[A <: AutoCloseable, Out](
    allocate: => A
  )(using: A => Try[Out]
  ): Try[Out] = Try(allocate) flatMap { resource =>
    try using(resource)
    catch { case error: Throwable => Failure(error) }
    finally resource.close()
  }

}

private object JarUtils extends SafeUsingCloseable {
  import java.util.zip._
  import scala.jdk.CollectionConverters._

  private type ZipEntryPredicate = ZipEntry => Boolean
  private val matchAll: ZipEntryPredicate = { _ => true }

  def getResourcesFromJarFile(
    jarFile: File,
    matching: Option[Regex] = None
  ): Try[Seq[ResourceEntry]] = using(new ZipFile(jarFile)) { zf =>
    zf.entries.asScala.filter(byMatching(matching)).map(toEntry).toVector
  }

  @inline
  private final def byMatching(matching: Option[Regex]): ZipEntry => Boolean = {
    val cachedPredicate: ZipEntryPredicate = matching
      .map(regex => (ze: ZipEntry) => regex.findFirstIn(ze.getName).nonEmpty).getOrElse(matchAll)
    cachedPredicate
  }

  @inline
  private final def toEntry(ze: ZipEntry): ResourceEntry =
    if (ze.isDirectory) { DirectoryEntry(ze.getName) }
    else { FileEntry(ze.getName) }
}

private object FileUtils {
  private type Directory = java.io.File
  private type MatchPredicate = String => Boolean
  private val matchAll: MatchPredicate = { _ => true }

  def createTemporaryDirectory(
    prefix: String = "",
    deleteOnExit: Boolean = false
  ): Try[File] = Try {
    val tmpDirPath = java.nio.file.Files.createTempDirectory(prefix)
    val tmpDir = tmpDirPath.toFile
    if (deleteOnExit) {
      Runtime.getRuntime.addShutdownHook(new Thread(() => recursiveDelete(tmpDirPath)))
    }
    tmpDir
  }

  def getResourcesFromDirectory(
    dirFile: File,
    matching: Option[Regex] = None
  ): Try[Seq[ResourceEntry]] = Try {
    val matchPredicate = matching.fold(matchAll)(regex => regex.findFirstIn(_).nonEmpty)
    val parentDir = dirFile.toPath
    if (dirFile.exists && dirFile.isDirectory) {
      goFilesRec(List(dirFile), Vector.empty, parentDir, matchPredicate)
    } else { Seq.empty[ResourceEntry] }
  }

  def listFilesRecursive(dirFile: File): Set[File] = {
    @tailrec
    def loop(dir: Seq[File], acc: Set[File] = Set.empty): Set[File] = dir match {
      case d +: ds if d.isFile      => loop(ds, acc + d)
      case d +: ds if d.isDirectory => loop(ds ++ d.listFiles.toSeq, acc + d)
      case Nil                      => acc
    }

    loop(Seq(dirFile))
  }

  @tailrec
  private def goFilesRec(
    dirs: List[Directory],
    acc: Vector[ResourceEntry],
    parentDir: java.nio.file.Path,
    matching: MatchPredicate
  ): Seq[ResourceEntry] = dirs match {
    case Nil => acc
    case dir :: tail =>
      val (files, childDirectories) = dir.listFiles
        .partitionMap(item => if (item.isFile) Left(item) else Right(item))
      val matchingFiles = files.filter(f => matching(f.getCanonicalPath))
        .map(f => FileEntry(parentDir.relativize(f.toPath).toString))
      val matchingDir =
        if (matching(dir.getCanonicalPath)) {
          Some(DirectoryEntry(parentDir.relativize(dir.toPath).toString))
        } else { None }
      goFilesRec(tail ++ childDirectories, acc ++ matchingDir ++ matchingFiles, parentDir, matching)
  }

  private def recursiveDelete(path: Path): Unit = {
    import java.nio.file.{ Files, FileVisitResult, SimpleFileVisitor }
    val visitor = new SimpleFileVisitor[Path] {
      override def visitFile(file: Path, attrs: BasicFileAttributes) = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }
      override def postVisitDirectory(dir: Path, exc: IOException) = {
        Option(exc) match {
          case None      => Files.delete(dir); FileVisitResult.CONTINUE
          case Some(err) => throw err
        }
      }
    }
    try Files.walkFileTree(path, visitor)
    catch { case ioEx: IOException => println(s"Error deleting temporary files:\n$ioEx") }
  }

}
