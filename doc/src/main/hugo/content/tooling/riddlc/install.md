---
title: "Installing `riddlc`"
description: "How to install the RIDDL Compiler"
type: "page"
draft: "false"
weight: 30
---

This guide helps you to install the `riddlc` tool on your
local computer. Before installation, you will need to follow
the steps defined on the [build page]({{< relref "build.md" >}}).

## Universal Installer
RIDDL's build system can generate a universal installation
package that produces a zip file that can be unpacked and it
provides a script to run on MacOS, Windows, and Linux.

### 1. Build Universal Installer
```shell
> project riddlc
> universal:packageBin
``` 

### 2. Move Generated ZIP File
a. Step 1 will have produce a `.zip` file in the `riddlc/target/universal` directory.
b. Copy this `.zip` file to the machine on which you wish to install `riddlc`
c. On that machine, unpack the `.zip` file with  `unzip <path-to-zip>`
You will now have a directory named something like:`riddl-0.5.6/bin` but the
version # might be different. 
d. Put that `bin` directory  in your path 
e. Now you can just run `riddlc` from the command line

## Native Installer
TBD. Some day we will have a native installer for each platform.