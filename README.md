#### Akka-Streams Web Crawling Example 

This is a small project that shows how to use Akka, Akka-HTTP, and Akka-Streams in Scala to recursively consume a 
nested JSON structure, convert it into an internal model, and asynchronously (and recursively) populate those in
a simple linear (Akka) stream. 

I've also included an analogous approach (in dml.interview1.ActorBasedCrawler) - which is fine, but not nearly as
composable as the streams based approach. 

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
