credentials += Credentials(
  realm = "GitHub Package Registry",
  host = "maven.pkg.github.com",
  userName = sys.env.getOrElse("GITHUB_ACTOR", ""),
  passwd = sys.env.getOrElse("GITHUB_TOKEN", "")
)

resolvers += MavenRepository(
  "GitHub Package Registry",
  "https://maven.pkg.github.com/ossuminc/sbt-ossuminc/com/ossuminc"
)

ThisBuild / csrConfiguration := {
  csrConfiguration.value.withCredentials(
    Seq(
      lmcoursier.credentials.Credentials(
        "GitHub Package Registry",
        "maven.pkg.github.com",
        sys.env.getOrElse("GITHUB_ACTOR", ""),
        sys.env.getOrElse("GITHUB_TOKEN", "")
      )
    )
  )
}

addSbtPlugin("com.ossuminc" % "sbt-ossuminc" % "0.20.3" cross CrossVersion.binary)

// This enables sbt-bloop to create bloop config files for Metals editors
// Uncomment locally if you use metals, otherwise don't slow down other
// people's builds by leaving it commented in the repo.
// addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.4.6")

ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always"

libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0"
