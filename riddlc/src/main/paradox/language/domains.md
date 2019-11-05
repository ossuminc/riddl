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
domain Automotive {
  domain Cars { ??? }
  domain Trucks { ??? }
  domain Repairs { ??? }
}
```
This indicates that in the knowledge domain named Automotive there are three
subdomains of interst: Cars, Trucks and Repairs.  

Domains may contain the following constructs:

@@toc { depth=2 }


@@@ index

* [Types](types.md)
* [Topics](topics.md)
* [Contexts](contexts.md)
* [Interactions](interactions.md)

@@@
