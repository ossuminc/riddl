package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.SymbolTable
import com.reactific.riddl.language.testkit.ParsingTest
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import scala.io.Source

class GrpcWriterTest extends ParsingTest {

  val testDir = Files.createTempDirectory("grpc-writer")

  "GrpcWriter" must {
    "emit a kalix file header" in {
      val input =
        """
          |domain foo is {
          |  options(package("com.foo"))
          |}""".stripMargin
      parseTopLevelDomains(input) match {
        case Left(error) =>
          fail(error.map(_.format).mkString("\n"))
        case Right(root) =>
          val symtab = SymbolTable(root)
          val path = Paths.get("src","main","proto", "com", "foo", "api", "foo.proto")
          val file = testDir.resolve(path)
          val gw = GrpcWriter(file, root, symtab)
          gw.emitKalixFileHeader(Seq("com", "foo", "api"))
          gw.write()
          val writtenContent = Source.fromFile(file.toFile, "utf-8")
          val content = writtenContent.getLines().mkString("\n")
          writtenContent.close()
          val expected: String =
            """syntax = "proto3";
              |
              |package com.foo.api;
              |
              |import "google/api/annotations.proto";
              |import "kalix/annotations.proto";""".stripMargin
          content must be(expected)
      }
    }
  }
}
