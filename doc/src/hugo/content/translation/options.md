---
title: "riddlc options"
type: "page"
draft: "false"
weight: 5
---

## Introduction
Like many other complex programs, `riddlc` implements a variety of commands, 
each with their own functionality and their own options. As there are overlaps

{{% hint warning %}}
Options come and go as `riddlc` evolves. If you experience issues with the 
command line options, you can always run `riddlc` without any options and it
will explain the options your version supports. 
{{% /hint %}}

## Common Options
Some options are common to all the commands. 

### -h (--help)
Causes `riddlc` to print out help and usage information and exit.

### -t (--show-times)
Translation is done in various stages (parsing, validating, loading, 
translating, etc.). This option causes `riddlc` to print out how long each of
these stages takes

### -v (--verbose)
Causes `riddlc` to be more verbose in its output, what it is doing, etc. 
So far this doesn't add any additional output but the option is reserved
for this use case. 

### -q (--quiet)
Do not print out any output, just do the requested command.

### -d (--dry-run)
Go through the motions of processing the options and teeing up the command 
to run, but don't actually run it.

### -w (--suppress-warnings)
Causes all warnings to be squelched from the output. `riddlc` has many general
warnings, lots of warnings about missing things, and even stylistic or idiomatic
suggestions. This option suppresses all of them to make the output less verbose.

### -m (--suppress-missing-warnings)
Warnings about missing constructs are normally turned off, this option turns
them back on so the messages are put out by `riddlc`. This can be quiet verbose
in early RIDDL specifications that do not have much documentation in them.

### -s (--suppress-style-warnings)
Warnings about RIDDL style are normally turned off, this option turns them back
on so the messages are put out by `riddlc`

## `parse [options]` Command
The `parse` command causes riddlc to only parse the provided RIDDL file for 
syntactic compliance with the RIDDL language. No validation or translation is
done on the input. Note that `riddlc` terminates after the first syntax error
message has printed.

In addition to the common options, you can specify the options described in 
the following subsections. 

### `-i` (`--input-file`)
This is a required option that provides the file to be parsed. 

## `validate [options]` Command
This command does everything that the `parse` command does, but also 
semantically validates the input if parsing succeeds. 

In addition to the common options, you can specify the options described in 
the following subsections.

### `-i` (`--input-file`)
This is a required option that provides the file to be parsed.

## `reformat [options]` Command
This command regurgitates its input but in a consistent style with options to
affect that style in various ways. 

In addition to the common options, you can specify the options described in
the following subsections.

### `-i` (`--input-file`)
This is a required option that provides the file to be parsed.

### `-o` (`--output-dir`)
This is a required option that provides the directory into which the output
will be placed. Generated files will have the same names as the input files. 

### `-s` (`--single-file`)
This option causes all the `include` statements in the input to be removed and
the entire input generated into a single file. 

{{% hint warning %}}
Currently this option is forced on whether you specify the option or not. The
only output supported is a single file. This will be remedied in a later 
release.
{{% /hint %}}

## `hugo [options]` Command
This command causes `riddlc` to parse, validate and translate its input into 
the input needed for a hugo based website that described the RIDDL input. 

In addition to the common options, you can specify the options described in
the following subsections.

### `-i` (`--input-file`)
This is a required option that provides the file to be translated to a hugo
website.

### `-o` (`--output-dir`)
This is a required option that provides the directory into which the hugo
website source files will be placed. 

### `-p`, `--project-name <value>`
This options provides the `hugo` command with the overall name of the project
that is being described by the RIDDL input. This is used in the meta tags for
the title of the page, and other places where the title is needed.

### `-e`, `--erase-output <value>`
Erase the entire output directory before putting out any files. 

{{% hint warning %}}
It is highly recommended that you use this option, but it defaults to off to
prevent the new user from deleting the wrong directory structure. All files
under `--output-dir` will be unceremoniously deleted so specifying 
`--output-dir` incorrectly can yield significant data loss.  
{{% /hint %}}

### `-b`, `--base-url <value>`
This option provides the first part of the URL at which the generated site is
publicly accessible. It defaults to `http://localhost:1313` which is the default
for hugo. 

### `-s`, `--source-url <value>`
The generated site offers the ability to link to the source document for any 
page. The `<value>` provided must be a valid URL for a website.

{{% hint info %}}
This corresponds to the geekdoc hugo theme's `geekdocRepo` parameter. 
{{% /hint %}}

### `-h`, `--edit-path <value>`
This option is used to extend the funtionality of the `--source-url` option to
allow editing as well as linking to the source. This option provides the 
source repository's intermediate URL path that can be used to edit a page.

### `-l`, `--site-logo-url <value>`  
URL to the site's logo image for use by hugo

## `hugo-git-check [options]` Command
This command is the same as the hugo command, and takes the same options, 
except you also provide a URL to a directory in a git repository. Any 
changes to the git repository below that direction, as reported by git, 
will cause the hugo command to run. Otherwise nothing happens. This allows
an auto-update upon changes when combined with the `repeat` command. 

## `from <path-to-config-file>` Command
This command repeats 
## `repeat <cycle-delay> <max-cycles> <path-to-config-file` Command
This command repeats whichever command is specified in the configuration file
at `<path-to-config-file>`. It will cycle `<max-cycles>` times and insert a 
delay of `<cycle-delay>` which must be specified with a duration suffix like
`s`(seconds) `m`(minutes), etc. The full set of possibilities are 
[defined here](https://github.com/lightbend/config/blob/main/HOCON.md#duration-format)

the input needed for a hugo based website that described the RIDDL input.

In addition to the common options, you can specify the options described in
the following subsections.

## Options From Configuration Files
The `from` and `repeat` commands use HOCON configuration files to determine
what to do. The HOCON syntax is [fully explained here](https://github.
com/lightbend/config/blob/main/HOCON.md) but it is simpler than that in the
case of riddlc, just follow these rules:
* Assign the command you want to run to the "command" option.
* All the common options can be specified at the top level. 
* Command specific options must appear in a section named after the command.
* All the configuration items have the same names as the long form names that  
  riddlc prints out with the `help` command. 

So, for example, to reformat a file to another file without warnings you 
could set up a configuration file like this:
```hocon
command = reformat
suppress-warnings = true
reformat {
  input-file = "path/to/input.riddl"
  output-dir = "path/to/output/dir"
  single-file = true
}
```
Similarly for other commands. You can even specify all the options for all the
commands and then just change the command selector when you want to do 
something different. Or, put them each in separate files and use commandline 
completion to specify which file. 

## Example of `riddlc help` command
```text
RIDDL Compiler (c) 2022 Reactive Software LLC. All rights reserved. 
Version:  0.2.1-131-f6486929 

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
