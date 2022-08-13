package com.reactific.riddl.utils

import java.nio.file.Path
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarFile}
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.nio.file.Files
import java.nio.file.Paths

object Tar {

  def copyFileEntry(entry: TarArchiveEntry, directory: Path): Array[Byte] = {
    require(!entry.isDirectory, s"${entry.getName} should not be a directory")
    val fileSize = entry.getSize
    val readSize = Math.max(fileSize, 1024*1024*10L)
    val content: Array[Byte] = new Array[Byte](readSize)
    val bytesRemaining = entry.getSize
    while (bytesRemaining > 0) {
      entry.read(content, offset, content.length - offset);

    LOOP UNTIL entry.getSize() HAS BEEN READ {
    }
  }

  def untar(tarFile: Path, destDir: Path) : Either[String,Boolean] = {
    if (tarFile.endsWith(".gz")) {
      try {
        val fi = Files.newInputStream(tarFile)
        val gzi = new GzipCompressorInputStream(fi)
        val i = new TarArchiveInputStream(gzi)

        val fin = Files.newInputStream(Paths.get("archive.tar.gz"))
        val in = new BufferedInputStream(fin)
        val out = Files.newOutputStream(Paths.get("archive.tar"))
        val gzIn = new GzipCompressorInputStream(in)
        val buffer = new Array[Byte](buffersize)
        var n = 0
        while ( {-1 != (n = gzIn.read(buffer))}) out.write(buffer, 0, n)
        out.close()
        try {
        } finally {
          if (fi != null) fi.close()
          if (gzi != null) gzi.close()
          if (i != null) i.close()
        }
      }
    }
      // decompress from gzip format first
    }
  }
  val rc = Process(s"tar zxf $fileName", cwd = destDir.toFile).!
  if (rc != 0) {throw new IOException(s"Failed to unzip $zip_path")}
  zip_path.toFile.delete()


}
