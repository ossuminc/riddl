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
% cd riddl # top level directory of repository 
% sbt
> ; clean ; test ; test ; test
> project plugin
> scripted  
...
[info] All tests passed.
```
If all tests do not pass, stop and fix the software. Note that the tests are run
three times quickly. This tends to expose parallelism issues. 

## Stage
```shell
> sbt stage
...
[info] Main Scala API documentation successful.
[success] Total time: 29 s, completed Sep 5, 2022, 12:05:58 PM
```
If this task does not successfully complete, stop and fix the documentation which
is likely to be references to Scala classes in the docs between `[[` and `]]`

## Test On A Major Project
Now that riddlc is staged, run the `hugo` command on a large multi-file 
specification such as
[the Improving App](https://github.com/improving-ottawa/improving-app-riddl). 
This will expose any language change issues. If this doesn't pass, go back
and fix the software again and start from scratch. The test case named 
`RunRiddlcOnArbitraryTest` will do this for you if you clone that repo and 
adjust the paths in that test case for your clone. The test will just succeed
if it can't find those files locally. 

## Commit Changes
You've probably made changes as a result of the above. Commit those to a branch
(except the change to `RunRiddlcOnArbitraryTest`) and push it to GitHub (origin)

## Make a Pull Request
Use the branch you just created on GitHub to make a pull request and wait for
the workflow(s) to complete. If it does not pass all workflows, resolve those
issues and start this releasing process from the beginning. If it does pass the
workflows, merge it to `main` branch

## Check Out Main branch
```shell
git checkout main
```
Make sure there are no changes in your working tree.  One way to do this is to
```shell
get status
```
If that says:
```shell
On branch main
Your branch is up to date with 'origin/main'

nothing to commit, working tree clean
```
then you're okay to proceed; otherwise, fix the issues and restart this 
releasing process.

## Pick a Version Number
We are trying to follow [semantic versioning rules](https://semver.org/) 
for RIDDL. Use GitHub's features to 
assess what has changed in the version you are releasing. If the language has
changed syntax or semantics you must increase the major version number. Try to
batch such changes.

Pick a version number, x.y.z, based on current tagged version and the nature 
of the changes and the semver.org rules.

## Set Version
Now that:
* you've got things working,
* all your code changes are committed and pushed,
* you've got a clean working tree on the `main` branch,
* you've picked a version number

it is time to 

1. Formulate a short description string for the release
2. Run this:
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
1. When that is complete, log in to 
   [Sonatype OSS Repo](https://oss.sonatype.org/#stagingRepositories)
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
> graalvm-native-image:packageBin
> 
```

## Create Release On GitHub
```shell
open https://github.com/ossuminc/riddl/releases/new
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
* Open https://github.com/ossuminc/riddl/edit/main/actions/get-riddlc/action.yaml
* Scroll to the bottom of the page
* Update the version number value set in the `version` variable to 
  the ${x.y.z} version you released above.
* Commit, push, merge.

The change to this file does not need to be included in the release tag. 
Always do this last because other projects are dependent on this action and the
action is dependent on the uploaded artifacts.
