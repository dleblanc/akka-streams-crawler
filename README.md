#### This is the code for the Algorithmia interview challenge: (http://algo.work/interview)

This approach uses Akka Streams and Akka-HTTP to crawl a nested JSON structure. You can run it locally
via "LocalRunner", or host it on Algorithmia via the Interview1 class.

It is also fairly well tested, see Interview1Test.

It scales well to many thousands of resources (see below for testing), and uses a streaming Futures oriented
approach, so fetches resources in an eager yet non-blocking fashion.

* Local testing:
  Start up a docker container as follows:
    > run -p 9000:8080 -v `pwd`/local-json:/app bitnami/nginx:latest

  Populate some nested JSON files (edit the nesting level if you like):
    > cd local-json; ./populate.py

  Now you can hit localhost:9000/a, which will descend a large tree of JSON resources. A tree depth of 13 yields about
  8000 JSON files.



