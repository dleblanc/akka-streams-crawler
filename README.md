#### Akka-Streams Web Crawling Example 

This is a small project that shows how to use Akka, Akka-HTTP, and Akka-Streams in Scala to recursively consume a 
nested JSON structure, convert it into an internal model, and asynchronously (and recursively) populate those in
a simple linear (Akka) stream.

Problem statement: Recursively summarize the 'rewards' from a number of JSON resources, retrieved via HTTP from
a single root resource. Each resource may specify 0 or more children that should be recursively retrieved.
All requests should be performed as eagerly as possible (no unnecessary blocking). This tree of resources
is fully consumed when all resources yield no more children. 

Each of the JSON files looks as follows (for an example resource at http://web.site/path/b):

```json
{
  "children":[
    "http://web.site/path/b",
    "http://web.site/path/c"
  ],
  "reward":1
} 
```

I've provided two solutions: an Actor-based approach (dml.interview1.ActorBasedCrawler) and an approach based
on Akka Streams (dml.interview1.StreamBasedCrawler). The latter is the preferred solution, and the only one I've
included unit tests for (the actor approach is only left for posterity, it was the initial approach). 

You can run it locally via running the "dml.interview1.StreamBasedCrawler" main class (or use SBT below).

It's well (unit) tested, please see dml.interview1.StreamBasedCrawlerTest. 

It scales well to many thousands of resources (see below for testing), and uses a nested Futures oriented
approach, so fetches resources in an eager yet non-blocking fashion.

Local testing:

 * We provide a test data generation script for producing some sample JSON files, invoked as follows:
 
  > cd local-json; ./populate.py; cd ..

 * You can easily serve these files in a container as follows:
 
  > docker run -p 9000:8080 -v `pwd`/local-json:/app bitnami/nginx:latest

 
Now you can hit localhost:9000/a, which will descend a large tree of JSON resources. A tree depth of 8 yields about
  255 JSON files. Note that this is only a simple stress test, and doesn't include other edge cases.


Now that you have the JSON files created, and docker loaded, you can run the program as follows:

  > sbt "run localhost:9000/a"
