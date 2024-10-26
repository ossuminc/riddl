pullRequests.frequency = "@weekly"

dependencyOverrides = [
  { groupId = "com.github.sbt", artifactId = "sbt-native-packager" }
]

updates.ignore = [
  { groupId = "org.scoverage", artifactId = "sbt-scoverage" }
  { groupId = "org.scala-lang", artifactId = "scala-library" }
]

dependencyOverrides = [{
  pullRequests = { frequency = "30 days" },
  dependency = { groupId = "org.wvlet.airframe" }
}]
