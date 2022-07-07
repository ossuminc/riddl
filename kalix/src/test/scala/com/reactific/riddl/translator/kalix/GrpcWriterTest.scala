package com.reactific.riddl.translator.kalix

import com.reactific.riddl.language.AST._
import com.reactific.riddl.language.parsing.FileParserInput
import com.reactific.riddl.language.testkit.ParsingTest
import org.scalactic.source.Position
import org.scalatest.BeforeAndAfter
import org.scalatest.BeforeAndAfterAll

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import scala.io.Source
import org.apache.commons.io.FileUtils

class GrpcWriterTest extends ParsingTest with BeforeAndAfterAll {

  val testDir: Path = Files.createTempDirectory("grpc-writer")

  override def afterAll(): Unit = {
    super.afterAll()
    FileUtils.deleteDirectory(testDir.toFile)
  }

  def run(domainInput: String): (GrpcWriter, Domain) = {
    val input =
      s"domain foo is {\n  options(package(\"com.foo\"))\n\n$domainInput\n}"

    parseTopLevelDomains(input) match {
      case Left(error) => fail(error.map(_.format).mkString("\n"))
      case Right(root) =>
        val pkgs = Seq("com", "foo", "api")
        val path = Paths
          .get("src", (Seq("main", "proto") ++ pkgs :+ "foo.proto"): _*)
        val file = testDir.resolve(path)
        GrpcWriter(file, pkgs, Seq.empty[Parent]) -> root.contents.head
    }
  }

  def load(gw: GrpcWriter): String = {
    gw.write()
    val writtenContent = Source.fromFile(gw.filePath.toFile, "utf-8")
    val content = writtenContent.getLines().mkString("\n")
    writtenContent.close()
    content
  }

  "GrpcWriter" must {
    "emit a kalix file header" in {
      val (gw, domain) = run("")
      gw.emitKalixFileHeader
      val content = load(gw)
      val expected: String = """syntax = "proto3";
                               |
                               |package com.foo.api;
                               |
                               |import "google/api/annotations.proto";
                               |import "kalix/annotations.proto";
                               |import "validate/validate.proto";""".stripMargin
      content must be(expected)
    }

    "emit a simple message type" in {
      val input = """type bar is command {
                    |  num: Number,
                    |  str: String,
                    |  bool: Boolean,
                    |  int: Integer,
                    |  dec: Decimal,
                    |  real: Real,
                    |  date: Date,
                    |  time: Time,
                    |  dateTime: DateTime,
                    |  ts: TimeStamp,
                    |  dur: Duration,
                    |  latLong: LatLong,
                    |  url: URL,
                    |  bstr: String(3,30),
                    |  range: range(0,100),
                    |  id: Id(That.Entity)
                    |}
                    |""".stripMargin
      val (gw, domain) = run(input)
      val typ = domain.types.head
      gw.emitMessageType(typ)
      val content = load(gw)
      val expected = """message bar { // bar
                       |  sint64 num = 1;
                       |  string str = 2;
                       |  bool bool = 3;
                       |  sint32 int = 4;
                       |  string dec = 5;
                       |  double real = 6;
                       |  Date date = 7;
                       |  Time time = 8;
                       |  DateTime dateTime = 9;
                       |  sint64 ts = 10;
                       |  Duration dur = 11;
                       |  LatLong latLong = 12;
                       |  string url = 13;
                       |  string bstr = 14;
                       |  sint32 range = 15;
                       |  string id = 16;
                       |}""".stripMargin
      content must be(expected)
    }

    "emit an event sourced entity for a riddl entity" in {
      val input = new FileParserInput(
        Path.of("kalix/src/test/input/entity/event-sourced/ExampleApp.riddl")
      )
      parseTopLevelDomains(input) match {
        case Left(errors) => fail(errors.map(_.format).mkString("\n"))
        case Right(root) =>
          val path = Paths
            .get("src", "main", "proto", "com", "foo", "api", "foo.proto")
          val file = testDir.resolve(path)
          val gw = GrpcWriter(file, Seq("com", "foo", "api"), Seq.empty[Parent])
          val context: Context = root.contents.head.includes.head.contents.head
            .asInstanceOf[Context]
          val entity = context.entities.head
          gw.emitKalixFileHeader
          gw.emitEntityApi(entity)
          gw.write()
          val writtenContent = Source.fromFile(file.toFile, "utf-8")
          val content = writtenContent.getLines().mkString("\n")
          writtenContent.close()
          val expected =
            """syntax = "proto3";
              |
              |package com.example.app.organization.api;
              |
              |import "kalix/annotations.proto";
              |import "google/api/annotations.proto";
              |// import "google/protobuf/empty.proto";
              |
              |message OrganizationId {
              |  string orgId = 1 [(kalix.field).entity_key = true];
              |}
              |
              |message Address {
              |  // maybe put this in a "improving.app.api" package?
              |}
              |
              |message OrganizationInfo {
              |  string name = 1; //i.e. Provo High School. Must be unique within the organizational structure.
              |  string shortName = 2; //i.e. PHS
              |  Address address = 3; //required for BaseOrg. Optional for all other organizations.
              |  bool isPrivate = 4; //defaults to true
              |  string url = 5;
              |  OrganizationId parentOrg =6; //BaseOrganizations do not have a parent. All other organizations must have
              |   a parent. The BaseOrganization (only one per organizational structure) is the financially responsible
              |   party.
              |}
              |
              |message EstablishOrganization {
              |  OrganizationId orgId = 1;
              |  OrganizationInfo info = 2;
              |}
              |
              |message OrganizationEstablished {
              |  OrganizationId orgId = 1;
              |  OrganizationInfo info = 2;
              |  sint64 timestamp = 3;
              |}
              |
              |service OrganizationService {
              |  option (kalix.codegen) = {
              |    event_sourced_entity: {
              |      name: "com.improving.app.organization.domain.Organization"
              |      entity_type: "organization"
              |      state: "com.improving.app.organization.domain.OrgState"
              |      events: [
              |        "com.improving.app.organization.api.OrganizationEstablished"
              |      ]
              |    }
              |  };
              |
              |  rpc establishOrganization (EstablishOrganization) returns (OrganizationEstablished) {
              |    option (google.api.http) = {
              |      post: "/org/{org_name}/"
              |      body: "*"
              |    };
              |  }
              |
              |}""".stripMargin
          content must be(expected)

      }
    }
    "emit an action for a service/gateway context" in { pending }
  }
}
