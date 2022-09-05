updates.ignore = [ { groupId = "org.scoverage", artifactId = "sbt-scoverage" } ]
updates.ignore = [ { groupId = "org.scalameta", artifactId = "scalafmt-core" } ]
updates.ignore = [ { groupId = "org.scalameta", artifactId = "scalafmt-core" } ]
dependencyOverrides = [{
  pullRequests = { frequency = "@monthly" },
  dependency = { groupId = "com.github.sbt", artifactId = "sbt-native-packager" }
}]
dependencyOverrides = [{
  pullRequests = { frequency = "@monthly" },
  dependency = { groupId = "org.scala-lang", artifactId = "scala-library" }
}]