---
title: "riddlc Config File"
date: 2022-03-01T16:08:00-07:00
draft: false
weight: 30
---

You can store your options to `riddlc` in a file and use that
file with the `from` command. The configuration files use 
HOCON as the input format. Here's an example:

```hocon
# This file contains the input parameters to riddlc for use with `riddlc from` command.

# We want to run the "hugo" command in riddlc so that riddl input is converted to input for the hugo web site generator.
command = hugo

# This block provides options that are common to any command.
common = {
  show-times = true
  verbose = true
  quiet = false
  dry-run = false
  hide-warnings = true
  hide-missing-warnings = true
  hide-style-warnings = true
  debug = true
  show-unused-warnings = false
}

# This block provides options for the "hugo" command to translate riddl to a hugo web site.
hugo {
  input-file = "ImprovingApp.riddl"
  output-dir = "target/hugo/"
  erase-output = true
  project-name = "ImprovingApp"
  enterprise-name = "Improving Inc."
  site-title = "RIDDL Specification For improving.app"
  site-description = "This site provides the documentation generated from the RIDDL specification for the improving.app"
  site-logo-url = "https://avatars.slack-edge.com/2022-08-03/3892148238579_bdc8d3ad2e5b91bd6cda_88.png"
  site-logo-path = "images/logo.png"
  erase-output = true
  base-url = "https://riddl.improving.app"
  source-url = "https://github.com/improving-ottawa/improving-app-riddl"
  with-glossary = true
  with-todo-list = true
  with-graphical-toc = false
}
stats {
  input-file = "ImprovingApp.riddl"
}
validate {
  input-file = "ImprovingApp.riddl"
}
```

## Example command syntax:

`riddlc from path/to/hocon/file/above validate`

This would use the "common" and "validate" sections of the hocon file for 
configuration and then run the validate command. The file validated is specified
by the `input-file` setting. The `common` options specify that the validate 
command, or any other command supported by the hocon file would:

* `show-times = true` - print out the durations of each of the phases
* `verbose = true` - print detailed information on what riddlc is doing
* `quiet = false` - print all messages at end of run
* `dry-run = false` - actually do the work, not just process the options
* `hide-warnings = true` - hide all kinds of warnings
* `hide-missing-warnings = true` - display no warnings about missing definitions
* `hide-style-warnings = true` - display no warnings about specification style
* `debug = true` - print debug info, typically only useful to implementors
* `show-unused-warnings = false` - display no warnings about unused definitions