package com.reactific.riddl.utils

import java.io.BufferedInputStream
import java.nio.file.Path
import java.nio.file.Files
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

object Tar {

  final val bufferSize: Int = 1024*1024 // 1 MB

  def untar(tarFile: Path, destDir: Path) : Either[String,Int] = {
    val fname = tarFile.getFileName.toString
    val fi = Files.newInputStream(tarFile)
    val bis = new BufferedInputStream(fi, bufferSize)

    val taris: TarArchiveInputStream = {
      if (fname.endsWith(".tar.gz")) {
        val gzis = new GzipCompressorInputStream(bis)
        new TarArchiveInputStream(gzis)
      } else if (fname.endsWith(".tar")) {
        new TarArchiveInputStream(bis)
      } else {
        return Left(s"Tar file name ${tarFile} must end in .tar.gz or .tar")
      }
    }

    var counter = 0
    var tae = taris.getNextTarEntry
    while (tae != null) {
      if (taris.canReadEntryData(tae)) {
        val path = destDir.resolve(Path.of(tae.getName))
        if (tae.isDirectory) {
          if (!Files.isDirectory(path)) {
            Files.createDirectories(path)
          }
        } else {
          val parent = path.getParent
          if (!Files.isDirectory(parent)) {
            Files.createDirectories(parent)
          }
          val o = Files.newOutputStream(path)
          try { IOUtils.copy(taris, o) } finally {o.close()}
          counter += 1
        }
      }
      tae = taris.getNextTarEntry
    }
    Right(counter)
  }
}
