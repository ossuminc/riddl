---
title: "How To Build `riddlc`"
description: "How to Build The RIDDL compiler"
type: "page"
draft: "false"
weight: 20
---

### 1. Obtain source code
```shell
> git clone https://github.com/reactific/riddl.git
> cd riddl
```

### 2. Install `JDK`

Please follow 
[the directions provided by Adoptium](https://adoptium.net/installation/) to 
install OpenJDK on your machine. For MacOS users, this boils down to:
```shell
brew install --cask temurin
```

### 3. Install `sbt`
Please follow [the directions to install sbt](https://www.scala-sbt.org/1.x/docs/Setup.html)
which orchestrates the build for RIDDL. For MacOS users, this boils down to:

```shell
> brew install sbt
```

### 4. Build
Within the cloned repository's directory (step 1 above), run this command:

```shell
> sbt "clean ; compile" 
```

