// GitHub Packages resolver for sbt-ossuminc

resolvers += "GitHub Packages" at "https://maven.pkg.github.com/ossuminc/sbt-ossuminc"

// Credentials MUST live in the meta-build (here), not only in the global
// ~/.sbt/2/github.sbt: under sbt 2 the global credentials file is not applied to
// meta-build (plugin) resolution, so plugin fetches from GitHub Packages get a
// 401 despite a valid GITHUB_TOKEN. Reads GITHUB_TOKEN from the environment
// (the CI Actions token, or a local PAT exported as GITHUB_TOKEN).
credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "x-access-token",
  sys.env.getOrElse("GITHUB_TOKEN", "")
)

addSbtPlugin("com.ossuminc" % "sbt-ossuminc" % "3.1.0")
