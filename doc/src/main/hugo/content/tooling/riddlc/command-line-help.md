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

## Available Commands

The following commands are available in `riddlc`:

| Command | Description |
|---------|-------------|
| `about` | Print out information about RIDDL |
| `bastify` | Convert a RIDDL file to BAST (Binary AST) format |
| `dump` | Dump the AST of the input file |
| `unbastify` | Convert a BAST file back to RIDDL source files |
| `flatten` | Flatten all includes into a single file |
| `from` | Load a configuration file and execute a command from it |
| `help` | Print out how to use this program |
| `info` | Print out build information about this program |
| `onchange` | Watch a directory and run a command when changes occur |
| `parse` | Parse the input file and report any errors |
| `prettify` | Reformat RIDDL source to a standard layout |
| `repeat` | Repeatedly run a command for edit-build-check cycles |
| `stats` | Generate statistics about a RIDDL model |
| `validate` | Parse and validate the input file |
| `version` | Print the version of riddlc and exit |

{{% hint info %}}
**Note**: Hugo documentation generation and diagram generation have been moved
to the [riddl-gen](https://github.com/ossuminc/riddl-gen) repository. Use that
tool if you need to generate Hugo sites or diagrams from RIDDL models.
{{% /hint %}}

## Info Command Output
For this version of `riddlc`:
```shell
> riddlc info
[info] About riddlc:
[info]            name: riddlc
[info]         version: 1.1.2
[info]   documentation: https://riddl.tech
[info]       copyright: © 2019-2026 Ossum Inc.
[info]        licenses: Apache License, Version 2.0
[info]    organization: Ossum Inc.
[info]   scala version: 3.3.7
```

## Common Options

The following options apply to all commands:

```
  -t | --show-times
        Show parsing phase execution times
  -I | --show-include-times
        Show parsing of included files execution times
  -d | --dry-run
        go through the motions but don't write any changes
  -v | --verbose
        Provide verbose output detailing actions taken by riddlc
  -D | --debug
        Enable debug output. Only useful for riddlc developers
  -q | --quiet
        Do not print out any output, just do the requested command
  -a | --no-ansi-messages
        Do not print messages with ANSI formatting
  -w <value> | --show-warnings <value>
        Suppress all warning messages so only errors are shown
  -m <value> | --show-missing-warnings <value>
        Suppress warnings about things that are missing
  -s <value> | --show-style-warnings <value>
        Suppress warnings about questionable input style
  -u <value> | --show-usage-warnings <value>
        Suppress warnings about usage of definitions
  -i <value> | --show-info-messages <value>
        Suppress information output
  -S <value> | --sort-messages-by-location <value>
        Print all messages sorted by file name and line number
  -G <value> | --group-messages-by-kind <value>
        Print all messages grouped by severity
  -x <value> | --max-parallel-parsing <value>
        Maximum number of include files parsed in parallel
  --max-include-wait <value>
        Maximum time to wait for include file parsing
  --warnings-are-fatal <value>
        Makes validation warnings fatal
  -B | --auto-generate-bast
        Automatically generate .bast files after parsing
```

## Command Details

### bastify
Convert a RIDDL file to BAST (Binary AST) format for faster loading:
```shell
riddlc bastify input-file.riddl
```
Creates a `.bast` file next to the input file.

### unbastify
Convert a BAST file back to RIDDL source:
```shell
riddlc unbastify input-file.bast -o output-dir
```

### from
Load options from a HOCON configuration file:
```shell
riddlc from config-file.conf target-command
```

### prettify
Reformat RIDDL source to a standard layout:
```shell
riddlc prettify input-file.riddl -o output-dir
```
Options:
- `--project-name <value>` - Project name for the output
- `-s | --single-file <value>` - Resolve all includes into a single file

### validate
Parse and validate a RIDDL file:
```shell
riddlc validate input-file.riddl
```

### stats
Generate statistics about a RIDDL model:
```shell
riddlc stats -I input-file.riddl
```