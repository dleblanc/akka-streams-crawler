package dml.interview1

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ujson.{Js, Transformable}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

/*
  * This is an actor based approach for recursively crawling the nested JSON structure in a non-blocking, eager manner.
  * Note that the Streams approach is probably preferable, as it is much more composable, and supports backpressure.
  *
  * Design:
  *  - We create a single instance of the crawler actor, initialized with an empty map of Uri->Result future.
  *  - We then send that actor a ResourceRequest to fetch the root URI. The actor requests that data using Akka-HTTP and
  *     performs requests of the nested resources, composing these into a single future that encapsulates the entire
  *     response. (Akka HTTP requests are handled by sending a message back to the 'self' actor).
  *
  *  - The caller then flattens (un-nests) the nested result structure and sums up the rewards.
  *
  * uriToNodeFuture is a collection of the URI to the future that holds the result of the fetch. Used so we know
  *                        which URIs we've visited so we don't fetch duplicate resources.
  */
class ActorBasedCrawler(uriToNodeFuture: collection.mutable.Map[String, Unit]) extends Actor {
  private[this] val REQUEST_TIMEOUT = 10.seconds
  private[this] val LONG_TIMEOUT = Timeout(5.minutes)

  private[this] val log = Logging(context.system, this)
  import context.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def receive: PartialFunction[Any, Unit] = {

    case ResourceRequest(uri) =>

      implicit val executionContext: ActorSystem = context.system

      if (uriToNodeFuture.contains(uri)) {
        log.debug("Skipping duplicated resource: " + uri)

        // Don't bother counting cycles - return an empty node
        sender ! None
      } else {
        log.info("Making a request for URI: " + uri)

        uriToNodeFuture.put(uri, Unit)
        val requestFuture = Http()
          .singleRequest(HttpRequest(uri = uri))
          .map { (_, uri)}
          .pipeTo(self)(sender)
      }

    case (HttpResponse(StatusCodes.OK, _, entity, _), uri: String) =>

      val jsonEntityFuture = entity
        .toStrict(REQUEST_TIMEOUT)
        .map {
          _.data
        }
        .map {
          _.utf8String
        }
        .map { utf8Entity => ujson.read(Transformable.fromString(utf8Entity)) }

      jsonEntityFuture.foreach { _ => log.info(s"Received a response for uri: $uri") }

      parseRequest(jsonEntityFuture, uri)
        .pipeTo(sender)

    case (resp@HttpResponse(code, headers, _, _), uri) =>
      log.error("Request failed, response code: " + code)
      resp.discardEntityBytes()
      throw new Exception(s"Error fetching resource: $uri")
  }

  def parseRequest(jsonEntityFuture: Future[Js.Value], uri: String): Future[Option[UriResult]] = {


    // We invoke a recursive call (actor call) for the children here - a list of futures, we then compose a Future which
    // will contain the contents of those and the contents of this future, as a Future[Node] with all children resolved

    val thisNodeFuture = jsonEntityFuture.map { json =>
      UriResult(uri, json("reward").num, Nil)
    }

    val childNodesFuture = jsonEntityFuture.flatMap { json =>

      val childUris = try {
        json("children")
          .arr
          .map {
            _.str
          }
          .toList
      } catch {
        case _: NoSuchElementException => Nil
      }

      implicit val childResolveTimeout: Timeout = LONG_TIMEOUT

      // Make the recursive requests, and convert it into a single Future representing the response
      Future.sequence(childUris.map { uri => (self ? ResourceRequest(uri)).mapTo[Option[UriResult]] })
    }

    for (thisNode <- thisNodeFuture;
         childNodes <- childNodesFuture)
      yield {
        Some(thisNode.copy(children = childNodes.flatten))
      }
  }

}

case class ResourceRequest(uri: String)

case class UriResult(uri: String, reward: Double, children: List[UriResult]) {

  def format(indent: Int = 0): String = {

    val indentStr = Array.fill(indent)("  ").mkString("")

    val childrenStr = children
      .map { _.format(indent + 1) }
      .mkString(",")

    s"""${indentStr}uri: $uri, reward: $reward, children: [
       |$childrenStr
       |$indentStr]""".stripMargin
  }

  def flatten(): List[UriResult] = this :: children.flatMap { node => node.flatten() }

}

object ActorBasedCrawler {

  // Can run this locally, if desired
  def main(args: Array[String]): Unit = {

    val actorSystem = ActorSystem()
    val rootUri = args.headOption.getOrElse("http://algo.work/interview/a")

    val sharedFetchUrlMap = collection.mutable.Map[String, Unit]()
    val crawlerActor = actorSystem.actorOf(Props(new ActorBasedCrawler(sharedFetchUrlMap)))

    implicit val timeout: Timeout = Timeout(3.minutes)
    val future = (crawlerActor ? ResourceRequest(rootUri)).mapTo[Option[UriResult]]

    implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher
    val recoveredFuture = future
      .filter { _.isDefined }
      .map { _.get.flatten() }
      .recover {
        case ex =>
          actorSystem.log.error(ex, "Error occurred: ")
      }
      .andThen { case _ => actorSystem.terminate() }
      .mapTo[List[UriResult]]

    val sumFuture = recoveredFuture.map {
      _.map { _.reward }
        .sum
    }

    val sumOfRewards = Await.result(sumFuture, timeout.duration)

    printf("The recursive sum of all rewards is: %.2f\n", sumOfRewards)
  }

}
