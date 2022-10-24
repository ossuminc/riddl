pullRequests.frequency = "@weekly"

dependencyOverrides = [
  { groupId = "com.github.sbt", artifactId = "sbt-native-packager" }
]

updates.ignore = [
  { groupId = "org.scoverage", artifactId = "sbt-scoverage" }
  { groupId = "org.scalameta", artifactId = "scalafmt-core" }
  { groupId = "org.scalameta", artifactId = "sbt-scalafmt" }
  { groupId = "org.scala-lang", artifactId = "scala-library" }
]
