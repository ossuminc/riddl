---
title: "riddlc --help"
description: "How to get riddlc to produce help output"
date: 2022-02-24T14:19:59-07:00
draft: false
weight: 10
---

riddlc is the command that makes all the magic happen. As you will see, riddlc 
is a rich and powerful tool for processing RIDDL input. 

## Command Syntax overview
`riddlc` uses a sub-command structure. At a high level, the command line syntax
is very simple:
```shell
> riddlc [common options] command-name [command-options]
```

## Info Command Output
For this version of `riddlc`:
```shell
> content % riddlc info
[info] About riddlc:
[info]            name: riddlc
[info]         version: 0.14.0
[info]   documentation: https://riddl.tech
[info]       copyright: © 2019-2022 Ossum Inc.
[info]        built at: 2022-09-09 11:28:07.485-0400
[info]        licenses: Apache License, Version 2.0
[info]    organization: Reactific Software LLC
[info]   scala version: 2.13.8
[info]     sbt version: 1.7.1
```
the help text shows the commands and options available:

```
RIDDL Compiler © 2019-2022 Ossum Inc. All rights reserved."
Version: 0.13.3-2-49ff0a59-20220909-1104-SNAPSHOT

This program parses, validates and translates RIDDL sources to other kinds
of documents. RIDDL is a language for system specification based on Domain
Drive Design, Reactive Architecture, and distributed system principles.


Usage: riddlc [options]

  -t | --show-times
        Show compilation phase execution times 
  -d | --dry-run
        go through the motions but don't write any changes
  -v | --verbose
        Provide verbose output detailing riddlc's actions
  -D | --debug
        Enable debug output. Only useful for riddlc developers
  -q | --quiet
        Do not print out any output, just do the requested command
  -w | --suppress-warnings
        Suppress all warning messages so only errors are shown
  -m | --suppress-missing-warnings
        Show warnings about things that are missing
  -s | --suppress-style-warnings
        Show warnings about questionable input style. 
  -P <value> | --plugins-dir <value>
        Load riddlc command extension plugins from this directory.

Usage:  [about|dump|from|help|hugo|info|parse|repeat|validate|version] <args>...

Command: about
  Print out information about RIDDL

Command: dump input-file
    input-file

Command: from config-file target-command
  Loads a configuration file and executes the command in it
    config-file
          A HOCON configuration file with riddlc options in it.
    target-command
          The name of the command to select from the configuration file

Command: help
  Print out how to use this program

Command: hugo [options] input-file
  Parse and validate the input-file and then translate it into the input
  needed for hugo to translate it to a functioning web site.
    input-file
          required riddl input file to read
    -o <value> | --output-dir <value>
          required output directory for the generated output
    -p <value> | --project-name <value>
          optional project name to associate with the generated output
    -e <value> | --erase-output <value>
          Erase entire output directory before putting out files
    -b <value> | --base-url <value>
          Optional base URL for root of generated http URLs
    -t <value> | --themes <value>
          Add theme name/url pairs to use alternative Hugo themes
    -s <value> | --source-url <value>
          URL to the input file's Git Repository
    -h <value> | --edit-path <value>
          Path to add to source-url to allow editing
    -m <value> | --site-logo-path <value>
          Path, in 'static' directory to placement and use
          of the site logo.
    -n <value> | --site-logo-url <value>
          URL from which to copy the site logo.

Command: info
  Print out build information about this program

Command: parse input-file
    input-file

Command: repeat [options] config-file target-command [refresh-rate] [max-cycles]
  This command supports the edit-build-check cycle. It doesn't end
  until <max-cycles> has completed or EOF is reached on standard
  input. During that time, the selected subcommands are repeated.
    config-file
          The path to the configuration file that should be repeated
    target-command
          The name of the command to select from the configuration file
    refresh-rate
          Specifies the rate at which the <git-clone-dir> is checked
  for updates so the process to regenerate the hugo site is
  started
    max-cycles
          Limit the number of check cycles that will be repeated.
    -n | --interactive
          This option causes the repeat command to read from the standard
          input and when it reaches EOF (Ctrl-D is entered) then it cancels
          the loop to exit.

Command: validate input-file
    input-file

Command: version
  Print the version of riddlc and exits

```
