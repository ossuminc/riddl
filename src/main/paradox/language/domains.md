# Domains

A domain is the top definitional level in RIDDL. Domains in RIDDL are
domains of knowledge, for example an entire business, or some portion of it. 
The language uses the concept of a domain to group together a set of related
concepts. Within domains  you find `types`, `contexts`, `channels` and
 `interactions`.    

Domains can declare themselves as the subdomain of another domain. For
example, a Car is a subdomain of the more general Automotive
domain. In RIDDL you would write it like so:
```text
domain Automotive {}
domain Car is subdomain of Automotive {}
```
Note how readable that is. 

@@@ index

* [Types](types.md)
* [Channels](channels.md)
* [Contexts](contexts.md)

@@@
