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
common {
  # common options applicable to any command
  show-times = true
  verbose = true
  quiet = false
  dry-run = false
  show-warnings = true
  show-missing-warnings = false
  show-style-warnings = false
}
hugo {
  # Options applicable to the hugo documentation generator
  input-file = "examples/src/riddl/ReactiveBBQ/ReactiveBBQ.riddl"
  output-dir = "examples/target/translator/ReactiveBBQ"
  project-name = "Reactive BBQ"
  erase-output = true
  base-url = "https://riddl.tech"
  source-url = "https://github.com/reactific/riddl"
  edit-path = "blob/main/examples/src/riddl/ReactiveBBQ"
  site-logo-path = "images/RBBQ.png"
}
```