---
title: "sbt-riddl"
description: "The SBT plugin for RIDDL"
type: "page"
draft: false
weight: 30
---

RIDDL provides an  
[SBT plugin](https://www.scala-sbt.org/1.x/docs/Using-Plugins.html) for 
convenience in other projects that want to run 
[`riddlc`]({{< relref "../riddlc" >}}) easily.

To install the SBT plugin all you need to do is add the following to your 
`project/plugins.sbt` file:

```sbt
addSbtPlugin("com.reactific" %% "sbt-riddl" % "{version}")
```
Make sure to replace `{version}` with an 
[appropriate version](https;//github.com/reactific/riddl/releases)
 

Then, specify options in your `build.sbt` file like this:

```sbt
// Enable the plugin you installed
enablePlugins(RiddlSbtPlugin) 
// Specify the options to riddlc that you want to run when the `compile` 
// command is used. This allows riddlc to be a code generator
riddlcOptions := Seq(
  "--verbose", "from", "path/to/config/file", "hugo"
)
// Specify the minimum riddlc version to processor you RIDDL specification
riddlcMinVersion := "0.14.0"
```