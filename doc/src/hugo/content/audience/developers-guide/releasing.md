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
> sbt "clean ; test"
...
[info] All tests passed.
```

If all tests do not pass, stop and fix the software

## Set Version
1. Pick a version number, x.y.z, based on current version and 
   [semantic versioning rules](https://semver.org/)
2. Formulate a short description string for the release, call it desc 
```shell
% sbt
> git tag -a ${x.y.z} "${desc}"
> reload
> show version
> git push --tags
```
{{% hint warning "Running From SBT" %}}
When you run the git commands from the SBT command prompt, you must 
reload in order to get the correct build version number into the artifacts.
Failing to do this will attempt to release a snapshot version which won't 
go well for you.
{{% /hint %}}

## Release To Maven
1. First, publish the artifacts to oss.sonatype.org
```shell
% cd riddl
% sbt publishSigned
```
1. When that is complete, log in to https://oss.sonatype.org/#stagingRepositories
2. Check the staged artifacts for sanity. All modules should be published with 
   the same release number
3. Close the repository and add the release number in the notes
4. Press the Release button to publish to maven central

## Build Release Artifacts

```shell
% sbt
> project riddlc
> Universal/packageBin
> Universal/packageOsxDmg
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


## Update riddl-actions
* Open https://github.com/reactific/riddl-actions/edit/main/riddlc/action.yaml
* Scroll to the bottom of the page
* Update the version numbers (3 times) in the last lines of teh file to match
  the ${x.y.z} version you released above.
* Create a ${x.y.z} tag on your change and push it. 

## Update riddl-examples
* Open https://github.com/reactific/riddl-examples/edit/main/.github/workflows/gh-pages.yml
* On line 22 change the riddl-action version number at the end to ${x.y.z}
