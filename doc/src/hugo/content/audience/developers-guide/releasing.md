---
title: "Releasing RIDDL"
type: "page"
draft: "false"
weight: 999
---

This is a "how to" guide on releasing the software. 

## Build & Test
Make sure everything tests correctly from a clean start. 
```shell
> cd riddl # top level directory of repository 
> sbt "clean ; test"
...
[info] All tests passed.
```
If all tests do not pass, stop and fix the software

## Stage
```shell
> sbt stage
...
[info] Main Scala API documentation successful.
[success] Total time: 29 s, completed Sep 5, 2022, 12:05:58 PM
```
If this task does not successfully complete, stop and fix the documentation which
is likely to be references to Scala classes in the docs between `[[` and `]]`

## Set Version
Now that you've got things working, pick a version number and tag it:
1. Pick a version number, x.y.z, based on current tagged version and 
   [semantic versioning rules](https://semver.org/)
2. Formulate a short description string for the release
3. Run this:
```shell
> git tag -a ${x.y.z} -m "${release description}"
> sbt show version # confirm it is the version you just set
> git push --tags
```

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
