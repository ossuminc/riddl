[![Code Build Status](https://github.com/ossuminc/riddl/actions/workflows/scala.yml/badge.svg)](https://github.com/ossuminc/riddl/actions/workflows/scala.yml/badge.svg)
[![Documentation Build Status](https://github.com/ossuminc/riddl/actions/workflows/hugo.yml/badge.svg)](https://github.com/ossuminc/riddl/actions/workflows/hugo.yml/badge.svg)
[![Coverage Status](https://coveralls.io/repos/github/ossuminc/riddl/badge.svg?branch=main)](https://coveralls.io/github/ossuminc/riddl?branch=main)
[![Maven Central](https://img.shields.io/maven-central/v/com.ossuminc/riddlc_3.svg)](https://maven-badges.herokuapp.com/maven-central/com.ossuminc/riddlc_3)
[![Scala.js](https://www.scala-js.org/assets/badges/scalajs-1.16.0.svg)](https://www.scala-js.org)

[![CLA assistant](https://cla-assistant.io/readme/badge/ossuminc/riddl)](https://cla-assistant.io/ossuminc/riddl)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/ossuminc/riddl/master/LICENSE)

[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=bugs)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Duplicated Lines (%)](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=duplicated_lines_density)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=ossuminc_riddl&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=ossuminc_riddl)


# RIDDL

## Handy Links
* Full Documentation: https://riddl.tech
* Coveralls Code Coverage Report: https://coveralls.io/github/ossuminc/riddl?branch=main
* SonarCloud Reports: https://sonarcloud.io/project/overview?id=ossuminc_riddl

## Introduction
RIDDL, the Reactive Interface to Domain Definition Language, is a specification
lanugage and tooling to help capture requirements and specify designs for the
class of systems that can be designed with DDD and utilize a distributed, reactive
cloud native architecture.  


RIDDL allows subject matter experts, business analysts, and system architects to
work at a higher level of abstraction than they would if they were coding directly
in a programming language. RIDDL aims to relieve developers of the burden of 
maintaining infrastructural code through evolution of the system abstractions.

## Important Documentation Sections

* [Introduction](https://riddl.tech/introduction/) - answer the important initial 
  questions
* [Concepts](https://riddl.tech/concepts/) - provides a conceptual understanding 
  of RIDDL before diving into the details
* [Guides](https://riddl.tech/guides/) - four guides for different kinds of RIDDL audiences.
* [riddlc](https://riddl.tech/tooling/riddlc/) - various ways to obtain the RIDDL compiler

## Install With `brew`
TBD

## Quickly Building On Your Computer
To use `riddlc` locally and be able to update it with new changes, use this approach:
* `git clone https://github.com/ossuminc/riddl.git`
* Change the directory to that cloned repository
* Put the full path to `riddl/riddlc/target/universal/stage/bin` directory in your
  PATH variable
* Build the entire package with `sbt compile`
* Chane directory to the `riddlc` sub-project
* Run `sbt stage` to build the program into the`riddlc/target/universal/stage/bin` 
  directory
* To update, run `git pull` from the `riddl` cloned repository and rerun the
  `sbt stage` command in the `riddlc` sub-project  to rebuild. 

This allows you to both make local changes and pull in changes from others to
keep your local copy of `riddlc` up to date. 

## Usage
To get the most recent options, run `riddlc help`. That command will give you
the syntax for doing various things with the riddl compiler (`riddlc`)

## Version / Info
The `riddlc` compiler has two commands, `info` and `version` that just print
out information about the build, and the version number, respectively, and then exit. 

## Contributing
_Contributions are very welcome!_

If you see an issue that you'd like to see fixed or want us to consider a
change, the best way to make it happen is to help by submitting a 
pull request implementing it. We welcome contributions from all, even if
you are unfamiliar with RIDDL. We will endeavor to guide you 
through the process once you've submitted your PR. 

Please refer to the CONTRIBUTING.md file for more details about the workflow 
and general hints on preparing your pull request. You can also ask for
clarifications or guidance on GitHub issues directly.

The RIDDL family of repositories is owned by Ossum, Inc., and they require
the use of a 
[CLA (Contributor License Agreement)](https://cla-assistant.io/ossuminc/riddl).
You can sign at that link or be prompted to do so when you submit your first
Pull Request. 

# IntelliJ File Type & Color Scheme Support

The `language` directory contains two files that will improve the visual appeal of
RIDDL source code in your IntelliJ IDEA. To load them, follow the instructions below:

## File Type Support
This will provide support for the `.riddl` file type which you should use for
your RIDDL files. This is provided in [intellij-idea-riddl-file-type-settings.zip](intellij-idea-riddl-file-type-settings.zip)

* To load into IntelliJ:
  - Select `File -> Manage IDE Settings -> Import Settings...` from IntelliJ menu
  - Navigate to your cloned repository in the `language` folder
  - Select `intellij-idea-riddl-file-type-settings.zip`

## Color Scheme Support
Differentiating between keywords, readability words, definitions, punctuation, types, etc.
is supported through the use of color scheme settings in this file:
[intellij-idea-riddl-colour-scheme-settings.jar](intellij-idea-riddl-colour-scheme-settings.jar). Note that using this colour scheme
requires overriding other themes as only 1 scheme can be used in IDEA at a time. You
can switch themes in the Settings under `Editor --> Color Schemes`

* To load into IntelliJ:
  - Load the IntelliJ Settings dialog (`File -> Settings` or Settings gear on top right)
  - Navigate to the `Editor -> Color Scheme` settings
  - Click the `Show Scheme Actions` gear icon to the right of the scheme selector
  - Choose `Import Scheme...` from the pop up menu
  - Navigate to your cloned repository in the `language` folder
  - Select `intellij-idea-riddl-colour-scheme-settings.jar`
