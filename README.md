#### This is the code for the Algorithmia interview challenge: (http://algo.work/interview)

This approach uses Akka Actors and Akka-HTTP to crawl a nested JSON structure. You can run it locally
via "Crawler", or host it on Algorithmia via the Interview1 class.

It scales well to many thousands of resources (see below for testing), and uses a nested Futures oriented
approach, so fetches resources in an eager yet non-blocking fashion.

* Local testing:
  Start up a docker container as follows:
    > run -p 9000:8080 -v `pwd`/local-json:/app bitnami/nginx:latest

  Populate some nested JSON files (edit the nesting level if you like):
    > cd local-json; ./populate.py

  Now you can hit localhost:9000/a, which will descend a large tree of JSON resources. A tree depth of 13 yields about
  8000 JSON files. Note that this is only a simple stress test, and doesn't include other edge cases.