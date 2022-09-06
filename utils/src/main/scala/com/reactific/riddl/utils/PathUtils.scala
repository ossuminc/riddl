package com.reactific.riddl.utils

import java.io.{File, InputStream}
import java.net.URL
import java.nio.file.{Files, Path}
import java.nio.file.StandardCopyOption

object PathUtils {

  /**
   * Determine if a program is in the current system PATH environment var
   * @param program The name of the program to find
   * @return True if the program is in the path, false otherwise
   */
  def existsInPath(program: String): Boolean = {
    System.getenv("PATH").split(java.util.regex.Pattern.quote(
      File.pathSeparator)
    ).map(Path.of(_)).exists(p => Files.isExecutable(p.resolve(program)))
  }


  def copyURLToDir(from: URL, destDir: Path): String = {
    val nameParts = from.getFile.split('/')
    if (nameParts.nonEmpty) {
      val fileName = scala.util.Random.self.nextLong.toString ++ nameParts.last
      Files.createDirectories(destDir)
      val dl_path = destDir.resolve(fileName)
      val in: InputStream = from.openStream
      if (in == null) {
        ""
      } else {
        try {
          Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING)
        }
        finally if (in != null) in.close()
      }
      if (Files.isRegularFile(dl_path)) {
        fileName
      } else {
        ""
      }
    } else {""}
  }
}
