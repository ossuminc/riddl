---
title: "Using riddlc/actions/get-riddlc@main"
description: "How to use a released version of riddlc"
type: "page"
draft: "false"
weight: 20
---

If you plan to develop a RIDDL specification and place the code for it into
a GitHub repository, there is a GitHub action to automate the running 
of model validation and documentation generation in Pull Requests easier. 

## Defining A Workflow
The workflow shown below is sufficient for many projects. You should be
able to just copy and paste it to `.github/workflows/validate-riddl.yaml`
and all your `main` merges and pushes to a pull-request branch will 
automatically validate the source. 

```workflow
name: Validate RIDDL

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v2
    - name: Set Up JDK 17
      uses: actions/setup-java@v2
      with:
        java-version: '17'
        distribution: 'temurin'
    - name: Get riddlc
      uses: ossuminc/riddl/actions/get-riddlc@main
    - name: Validate improving-app model with riddlc
      run: |
        echo RIDDLC = "$RIDDLC"
        which riddlc
        riddlc from "src/main/riddl/ImprovingApp.conf" validate
      shell: bash
 
```

[GitHub actions and workflows are documented at this link](https://docs.github.com/en/actions) 
