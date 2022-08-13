---
title: "Releasing RIDDL"
type: "page"
draft: "false"
weight: 999
---

This is a "how to" guide on releasing the software. 

## Build & Test
```shell
> cd riddl # top level directory of repository 
> sbt test
...
[info] All tests passed.
```

If all tests do not pass, stop and fix the software

## Set Version
1. Pick a version number, x.y.z, based on current version and 
   [semantic versioning rules](https://semver.org/)
2. Formulate a short description string for the release, call it desc 
```shell
> git tag -a ${x.y.z} "${desc}"
> git push --tags
```

## Build Release Artifacts

```shell
sbt "project riddlc; Universal/packageBin ; Universal/packageOsxDmg ; publishSigned"
```

## Create Release On GitHub
```shell
open https://github.com/reactific/riddl/releases/new
```
* pick the tag that you just made 
* write the release notes

## Upload Artifacts

* Click the area on github new release page that says:
>  Attach binaries by dropping them here or selecting them.
* Attach these files:
  * riddl/riddlc/target/universal/riddlc-${x.y.z}.zip
  * riddl/riddlc/target/universal/riddlc-${x.y.z}.dmg



