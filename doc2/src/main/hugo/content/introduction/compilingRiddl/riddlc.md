---
title: "riddlc --help"
date: 2022-02-24T14:19:59-07:00
draft: true
weight: 10
---

riddlc is the command that makes all the magic happen. As you will see, riddlc is a rich and powerful command. 

You can get this help text on the command line by running ```riddlc --help```.

```
RIDDL Compiler (c) 2022 Reactive Software LLC. All rights reserved. 
Version:  0.5.0-11-e0fd90eb-20220301-1528 

This program parses, validates and translates RIDDL sources to other kinds 
of documents. RIDDL is a language for system specification based on Domain 
Drive Design, Reactive Architecture, and Agile principles.

Usage: riddlc [parse|validate|reformat|hugo|hugo-git-check|from|help|repeat] [options] <args>...

  -V | --version
        
  -h | --help
        Print out help/usage information and exit
  -t | --show-times
        Show compilation phase execution times 
  -d <value> | --dry-run <value>
        go through the motions but don't write any changes
  -v | --verbose
        Provide detailed, step-by-step, output detailing riddlc's actions
  -q | --quiet
        Do not print out any output, just do the requested command
  -w | --suppress-warnings
        Suppress all warning messages so only errors are shown
  -m | --suppress-missing-warnings
        Show warnings about things that are missing
  -s | --suppress-style-warnings
        Show warnings about questionable input style. 
Command: parse [options]
Parse the input for syntactic compliance with riddl language.
No validation or translation is done on the input
  -i <value> | --input-file <value>
        required riddl input file to read
Command: validate [options]
Parse the input and if successful validate the resulting model.
No translation is done on the input.
  -i <value> | --input-file <value>
        required riddl input file to read
Command: reformat [options]
Parse and validate the input-file and then reformat it to a
standard layout written to the output-dir.  
  -i <value> | --input-file <value>
        required riddl input file to read
  -o <value> | --output-dir <value>
        required output directory for the generated output
  -s <value> | --single-file <value>
        Resolve all includes and imports and write a single file with the same
        file name as the input placed in the out-dir
Command: hugo [options]
Parse and validate the input-file and then translate it into the input
needed for hugo to translate it to a functioning web site.
  -i <value> | --input-file <value>
        required riddl input file to read
  -o <value> | --output-dir <value>
        required output directory for the generated output
  -p <value> | --project-name <value>
        Optional project name to associate with the generated output
  -e <value> | --erase-output <value>
        Erase entire output directory before putting out files
  -b <value> | --base-url <value>
        Optional base URL for root of generated http URLs
  -t <value> | --themes <value>
        
  -s <value> | --source-url <value>
        URL to the input file's Git Repository
  -h <value> | --edit-path <value>
        Path to add to source-url to allow editing
  -l <value> | --site-logo-url <value>
        URL to the site's logo image for use by site
  -p <value> | --site-logo-path <value>
        Path, in 'static' directory to placement and use
        of the site logo.
Command: hugo-git-check [options] git-clone-dir
This command checks the <git-clone-dir> directory for new commits
and does a `git pull" command there if it finds some; otherwise
it does nothing. If commits were pulled from the repository, then
the hugo command is run to generate the hugo source files and hugo
is run to make the web site available at hugo's default local web
address:  |http://localhost:1313/

  git-clone-dir
        Provides the top directory of a git repo clone that
contains the <input-file> to be processed.
  -i <value> | --input-file <value>
        required riddl input file to read
  -o <value> | --output-dir <value>
        required output directory for the generated output
  -p <value> | --project-name <value>
        Optional project name to associate with the generated output
  -e <value> | --erase-output <value>
        Erase entire output directory before putting out files
  -b <value> | --base-url <value>
        Optional base URL for root of generated http URLs
  -t <value> | --themes <value>
        
  -s <value> | --source-url <value>
        URL to the input file's Git Repository
  -h <value> | --edit-path <value>
        Path to add to source-url to allow editing
  -l <value> | --site-logo-url <value>
        URL to the site's logo image for use by site
  -p <value> | --site-logo-path <value>
        Path, in 'static' directory to placement and use
        of the site logo.
Command: from [options] config-file
Load riddlc options from a config file
  config-file
        A HOCON configuration file with riddlc options
  -i <value> | --input-file <value>
        required riddl input file to read
  -o <value> | --output-dir <value>
        required output directory for the generated output
Command: help
Print out how to use this program
Command: repeat config-file [refresh-rate] [max-cycles]
This command supports the edit-build-check cycle. It doesn't end
until <max-cycles> has completed or EOF is reached on standard
input. During that time, the selected subcommands are repeated.

  config-file
        The path to the configuration file that should be repeated
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
```