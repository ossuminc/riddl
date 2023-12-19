---
---

After making documentation changes, it is best to visualize those changes
to check that the documentation is still generating correctly and readable.

This is pretty simple.

### Install Hugo
You need to make sure Hugo is installed:
```shell
brew install hugo  
```

### Run Hugo
Then run the hugo server:
```shell
cd doc/src/hugo
hugo server --disableFastRender -D
```
The `--disableFastRender` means that hugo won't cache results but read from
source on each request. The `-D` option instructs to load all pages, even
the ones marked as drafts. 

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
