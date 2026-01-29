---
title: "riddlc options"
type: "page"
draft: "false"
weight: 5
---

## Introduction

Like many other complex programs, `riddlc` implements a variety of commands,
each with their own functionality and options.

{{% hint warning %}}
Options come and go as `riddlc` evolves. Run `riddlc help` to see the options
your version supports.
{{% /hint %}}

{{% hint info %}}
**Note**: Hugo documentation generation and diagram generation have been moved
to the [riddl-gen](https://github.com/ossuminc/riddl-gen) repository.
{{% /hint %}}

## Common Options

These options apply to all commands:

| Option | Description |
|--------|-------------|
| `-t`, `--show-times` | Show parsing phase execution times |
| `-I`, `--show-include-times` | Show parsing of included files times |
| `-d`, `--dry-run` | Go through the motions but don't write changes |
| `-v`, `--verbose` | Provide verbose output |
| `-D`, `--debug` | Enable debug output (for developers) |
| `-q`, `--quiet` | No output, just execute the command |
| `-a`, `--no-ansi-messages` | Disable ANSI formatting in messages |
| `-w`, `--show-warnings` | Control warning message display |
| `-m`, `--show-missing-warnings` | Control missing definition warnings |
| `-s`, `--show-style-warnings` | Control style warning display |
| `-u`, `--show-usage-warnings` | Control usage warning display |
| `-i`, `--show-info-messages` | Control info message display |
| `-S`, `--sort-messages-by-location` | Sort messages by file and line |
| `-G`, `--group-messages-by-kind` | Group messages by severity |
| `-x`, `--max-parallel-parsing` | Max parallel include file parsing |
| `--max-include-wait` | Max time to wait for include parsing |
| `--warnings-are-fatal` | Treat warnings as errors |
| `-B`, `--auto-generate-bast` | Auto-generate .bast files after parsing |

## Command Reference

### `parse` Command

Parse a RIDDL file for syntactic compliance:
```shell
riddlc parse input-file.riddl
```

### `validate` Command

Parse and semantically validate a RIDDL file:
```shell
riddlc validate input-file.riddl
```

### `prettify` Command

Reformat RIDDL source to a standard layout:
```shell
riddlc prettify input-file.riddl -o output-dir
```

Options:
| Option | Description |
|--------|-------------|
| `-o`, `--output-dir` | Required output directory |
| `--project-name` | Project name for the output |
| `-s`, `--single-file` | Merge all includes into a single file |

### `bastify` Command

Convert RIDDL to Binary AST format:
```shell
riddlc bastify input-file.riddl
```

Creates a `.bast` file next to the input for faster subsequent loading.

### `unbastify` Command

Convert Binary AST back to RIDDL source:
```shell
riddlc unbastify input-file.bast -o output-dir
```

Options:
| Option | Description |
|--------|-------------|
| `-o`, `--output-dir` | Output directory (default: next to input) |

### `from` Command

Load options from a HOCON configuration file:
```shell
riddlc from config-file.conf target-command
```

### `repeat` Command

Support edit-build-check cycles by repeating a command:
```shell
riddlc repeat config-file.conf target-command [refresh-rate] [max-cycles]
```

Options:
| Option | Description |
|--------|-------------|
| `refresh-rate` | How often to check for changes |
| `max-cycles` | Maximum number of cycles |
| `-n`, `--interactive` | Exit on EOF from stdin |

### `onchange` Command

Watch a directory and run a command when changes occur:
```shell
riddlc onchange config-file.conf watch-directory target-command
```

### `stats` Command

Generate statistics about a RIDDL model:
```shell
riddlc stats -I input-file.riddl
```

## Configuration Files

The `from` and `repeat` commands use HOCON configuration files. Example:

```hocon
command = validate
show-warnings = true
validate {
  input-file = "path/to/input.riddl"
}
```

See [HOCON documentation](https://github.com/lightbend/config/blob/main/HOCON.md)
for syntax details.