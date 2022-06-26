package com.reactific.riddl.utils

import com.reactific.riddl.utils.TextFileWriter.*

import java.io.PrintWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Files
import java.nio.file.StandardCopyOption

import scala.annotation.tailrec
import scala.collection.mutable

/** Unit Tests For TextFileWriter */
abstract class TextFileWriter(filePath: Path) {

  protected val sb: mutable.StringBuilder = new mutable.StringBuilder()

  override def toString: String = sb.toString

  private def mkDirs(): Unit = {
    val dirFile = filePath.getParent.toFile
    if (!dirFile.exists) { dirFile.mkdirs() }
  }

  def write(writer: PrintWriter): Unit = {
    try {
      writer.write(sb.toString())
      writer.flush()
    } finally { writer.close() }
    sb.clear() // release memory because content written to file
  }

  def write(): Unit = {
    mkDirs()
    val writer = new PrintWriter(filePath.toFile)
    write(writer)
  }

  def nl: this.type = { sb.append("\n"); this }

  def fillTemplateFromResource(
    resourceName: String,
    substitutions: Map[String, String]
  ): Unit = {
    val src = this.getClass.getClassLoader.getResourceAsStream(resourceName)
    require(src != null, s"Failed too load '$resourceName' as a stream")
    val templateBytes = src.readAllBytes()
    val template = new String(templateBytes, StandardCharsets.UTF_8)
    val result = substitute(template, substitutions)
    sb.append(result)
  }
}

object TextFileWriter {

  def copyResource(resourceName: String, destination: Path): Unit = {
    val src = this.getClass.getClassLoader.getResourceAsStream(resourceName)
    Files.copy(src, destination, StandardCopyOption.REPLACE_EXISTING)
  }

  def copyURLToDir(from: Option[URL], destDir: Path): String = {
    if (from.isDefined) {
      import java.io.InputStream
      import java.nio.file.{ Files, StandardCopyOption }
      val nameParts = from.get.getFile.split('/')
      if (nameParts.nonEmpty) {
        val fileName = nameParts.last
        val in: InputStream = from.get.openStream
        destDir.toFile.mkdirs()
        val dl_path = destDir.resolve(fileName)
        Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING)
        fileName
      } else { "" }
    } else { "" }
  }

  def substitute(
    template: String,
    substitutions: Map[String, String]
  ): String = {
    val textLength = template.length
    val builder = new mutable.StringBuilder(textLength)

    @tailrec
    def loop(text: String): mutable.StringBuilder = {
      if (text.isEmpty) { builder }
      else if (text.startsWith("${")) {
        val endBrace = text.indexOf("}")
        if (endBrace < 0) { builder.append(text) }
        else {
          val replacement = substitutions.get(text.substring(2, endBrace))
            .orNull
          if (replacement != null) {
            builder.append(replacement)
            loop(text.substring(endBrace + 1))
          } else {
            builder.append("${")
            loop(text.substring(1))
          }
        }
      } else {
        val brace = text.indexOf("${")
        if (brace < 0) { builder.append(text) }
        else {
          builder.append(text.substring(0, brace))
          loop(text.substring(brace))
        }
      }
    }
    loop(template).toString()
  }
}
