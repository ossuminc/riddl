/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.utils.IOUtils

import java.io.BufferedInputStream
import java.nio.file.{Files, Path}

object Tar {

  final val bufferSize: Int = 1024 * 1024 // 1 MB

  def untar(tarFile: Path, destDir: Path): Either[String, Int] = {
    val fname = tarFile.getFileName.toString
    val fi = Files.newInputStream(tarFile)
    val bis = new BufferedInputStream(fi, bufferSize)

    val taris: Option[TarArchiveInputStream] = {
      if fname.endsWith(".tar.gz") then {
        val gzis = new GzipCompressorInputStream(bis)
        Some(new TarArchiveInputStream(gzis))
      } else if fname.endsWith(".tar") then Some(new TarArchiveInputStream(bis))
      else {
        None
      }
    }

    taris match
      case None =>
        Left(s"Tar file name $tarFile must end in .tar.gz or .tar")
      case Some(taris) =>
        var counter = 0
        var tae = taris.getNextEntry
        var buffer = new Array[Byte](4096)
        while tae != null do {
          if taris.canReadEntryData(tae) then {
            val path = destDir.resolve(Path.of(tae.getName))
            if tae.isDirectory then {
              if !Files.isDirectory(path) then { Files.createDirectories(path) }
            } else {
              val parent = path.getParent
              if !Files.isDirectory(parent) then { Files.createDirectories(parent) }
              val o = Files.newOutputStream(path)
              try {
                val len_read = IOUtils.readFully(taris, buffer)
                o.write(buffer, 0, len_read)
              } finally {
                o.close()
              }
              counter += 1
            }
          }
          tae = taris.getNextEntry
        }
        Right(counter)
  }
}
