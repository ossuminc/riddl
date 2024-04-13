import com.ossuminc.sbt.OssumIncPlugin
import com.ossuminc.sbt.OssumIncPlugin.autoImport.With
import com.ossuminc.sbt.helpers.{RootProjectInfo, Unidoc}
import sbt.*
import sbt.Keys.*
import sbt.Project.projectToRef
import sbtunidoc.BaseUnidocPlugin.autoImport.{unidoc, unidocProjectFilter}
import sbtunidoc.ScalaUnidocPlugin
import sbtunidoc.ScalaUnidocPlugin.autoImport.ScalaUnidoc
import scoverage.ScoverageSbtPlugin
import RootProjectInfo.Keys._

object DocSite {

  def apply(
    dirName: String,
    apiOutput: File = file("doc") / "src" / "main" / "hugo" / "static" / "apidoc",
    docProjects: Seq[(Project, Configuration)],
    logoPath: String = "doc/src/main/hugo/static/images/RIDDL-Logo-128x128.png"
  ): Project =
    Project
      .apply(dirName, file(dirName))
      .enablePlugins(OssumIncPlugin, ScalaUnidocPlugin)
      .configure(With.basic, With.scala3, Unidoc.configure)
      .disablePlugins(ScoverageSbtPlugin)
      .settings(
        name := dirName,
        publishTo := Option(Resolver.defaultLocal),
        Compile / doc / target := apiOutput,
        ScalaUnidoc / scalaVersion := (compile / scalaVersion).value,
        ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(docProjects.map(x => projectToRef(x._1)): _*),
        ScalaUnidoc / unidoc / target := apiOutput,
        Compile / doc / scalacOptions := Seq(
          "-project:RIDDL API Documentation",
          s"-project-version:${version.value}",
          s"-project-logo:$logoPath",
          s"-project-footer:Copyright ${projectStartYear.value} ${organizationName.value}. All Rights Reserved.",
          s"-revision:main",
          "-comment-syntax:wiki",
          s"-source-links:github://${gitHubOrganization.value}/${gitHubRepository.value}/main",
          s"-siteroot:${apiOutput.toString}", {
            val mappings: Seq[Seq[String]] = Seq(
              Seq(".*scala", "scaladoc3", "https://scala-lang.org/api/3.x/"),
              Seq(".*java", "javadoc", "https://docs.oracle.com/javase/8/docs/api/")
            )
            s"-external-mappings:${mappings.map(_.mkString("::")).mkString(",")}"
          }
        )
      )
}
