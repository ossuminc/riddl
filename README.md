[![Code Build Status](https://github.com/reactific/riddl/actions/workflows/scala.yml/badge.svg)](https://github.com/reactific/riddl/actions/workflows/scala.yml/badge.svg)
[![Documentation Build Status](https://github.com/reactific/riddl/actions/workflows/gh-pages.yml/badge.svg)](https://github.com/reactific/riddl/actions/workflows/gh-pages.yml/badge.svg)
[![Coverage](https://coveralls.io/repos/github/reactific/riddl/badge.svg?branch=coverage)](https://coveralls.io/github/reactific/riddl?branch=coverage)
[![CLA assistant](https://cla-assistant.io/readme/badge/reactific/riddl)](https://cla-assistant.io/reactific/riddl)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/reactific/riddl/master/LICENSE)

[![Lines of Code](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=ncloc)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)
[![Technical Debt](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=sqale_index)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)
[![Code Smells](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=code_smells)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=reactific_riddl&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=reactific_riddl)

# RIDDL

## Full Documentation
Is here: https://riddl.tech

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

* [Introduction](https://riddl.tech/introduction/) - answer the important initial questions
* [Concepts](https://riddl.tech/concepts/) - provides a conceptual understanding of RIDDL before diving into the details
* [Guides](https://riddl.tech/guides/) - four guides for different kinds of RIDDL audiences.
* [riddlc](https://riddl.tech/tooling/riddlc/) - various ways to obtain the RIDDL compiler

## Install With `brew`
TBD

## Quickly Building On Your Computer
To use `riddlc` locally and be able to update it with new changes, use this approach:
* `git clone` the latest content and change directory to that cloned repository
* Put the `.../riddl/riddlc/target/universal/stage/bin` directory in your PATH 
  variable
* Run `sbt stage` to build the program
* To update, run `git pull` from the `riddl` cloned repository directory and
  rerun the `sbt stage` command to rebuild. 

This allows you to both make local changes and pull in changes from others to
keep your local copy of `riddlc` up to date. 

## Usage
To get the most recent options, run `riddlc help`. That command will give you
the syntax for doing various things with the riddl compiler (`riddlc`)

## Version / Info
The `riddlc` compiler has two command, `info` and `version` that just print
out information and the version number, respectively, and then exit. 

### Contributing
_Contributions are very welcome!_

If you see an issue that you'd like to see fixed, or want us to consider a
change, the best way to make it happen is to help out by submitting a 
pull request implementing it. We welcome contributions from all, even if
you are not yet familiar with RIDDL. We will endeavour to guide you 
through the process once you've submitted your PR. 

Please refer to the CONTRIBUTING.md file for more details about the workflow, 
and general hints on how to prepare your pull request. You can also ask for
clarifications or guidance in GitHub issues directly

The RIDDL family of repositories are owned by Ossum, Inc. and they require
the use of a 
[CLA (Contributor License Agreement)](https://cla-assistant.io/reactific/riddl).
You can sign at that link or be prompted to do so when you submit your first
Pull Request. 

