---
title: "Checking Generated Documentation"
draft: false
weight: 30
---

After making code or documentation changes, it is a best practice to
generate the documentation so you can visualize those changes and check 
that your intention for the change materialized.

There are two kinds of documentation: scaladoc generated API documentation
for developers working with the RIDDL source code, and the main 
hugo generated user documentation. 

## Building The ScalaDoc
Scala 3 API documentation is generated from the source code. This is very
simple to do: 
```shell
sbt
> project root
> unidoc
> open doc/src/main/hugo/static/apidoc/index.html
```
That last command (on MacOS) will open the root of the API documentation 
in your browser. On other platforms, do something equivalent.

If you want to view the last released public version of the API
documentation it will be at [https://riddl.tech/apidoc](https://riddl.tech/apidoc)

## Building The Main Documentation

### Install Hugo
You need to make sure Hugo is installed:
```shell
brew install hugo  
```
but you only need to do this once. 

### Generate the API Doc
Follow the [instructions above](#building-the-scaladoc).

### Run Hugo
Then run the hugo server:
```shell
cd doc/src/main/hugo
hugo server --disableFastRender -D
```
The `--disableFastRender` means that hugo won't cache results but read from
source on each request. The `-D` option instructs to load all pages, even
the ones marked as drafts. The `hugo server` command, if left running, will
update its output when the input (in `doc/src/main/hugo/*`) changes. 

### Open Browser
To view the site in your browser:
```shell
open http://localhost:1313/
```
Note that if that port is busy, hugo may use another one. It will print
out the URL it used so you should just be able to click on it from your
terminal window. 

## Continue Editing
You can continue editing while the server is running. This allows you to
validate your work on the markdown files visually in the browser. Whenever
you change a markdown file, Hugo will notice and reload the browser page 
automatically if the viewed markdown page changed. 
