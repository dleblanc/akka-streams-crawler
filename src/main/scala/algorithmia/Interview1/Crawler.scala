package algorithmia.Interview1

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{Actor, ActorSystem, Props}
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import ujson.{Js, Transformable}

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

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

class CrawlerActor(uriToNodeFuture: scala.collection.concurrent.Map[String, Unit]) extends Actor {
  private[this] val REQUEST_TIMEOUT = 10.seconds
  private[this] val LONG_TIMEOUT = Timeout(5.minutes)

  private[this] val log = Logging(context.system, this)
  import context.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  override def receive = {

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

object Crawler {

  // Can run this locally, if desired
  def main(args: Array[String]): Unit = {

    val actorSystem = ActorSystem()
    val rootUri = args.headOption.getOrElse("http://algo.work/interview/a")

    val sharedFetchUrlMap = new ConcurrentHashMap[String, Unit]().asScala
    val crawlerActor = actorSystem.actorOf(Props(new CrawlerActor(sharedFetchUrlMap)))

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

    val sum = Await.result(sumFuture, timeout.duration)

    s"The sum of rewards is: $sum"
  }

}
