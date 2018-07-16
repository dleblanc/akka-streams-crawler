package algorithmia.Interview1

import java.io.File

import com.algorithmia._
import com.algorithmia.algo._
import com.algorithmia.data._
import com.google.gson._
import akka.actor.ActorSystem
import akka.event.DefaultLoggingFilter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import ujson.{Js, Transformable}

import scala.annotation.tailrec
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Future}

// Being lazy about defining execution contexts, this works fine for this purpose
import scala.concurrent.ExecutionContext.Implicits.global

class Interview1 {
  private[this] val requestTimeout = 1.minute
  private[this] val sumTimeout = 2.minutes

  // Akka doesn't like how Algorithmia packages up the resources
  private[this] val config =  ConfigFactory
    .parseResources(classOf[Interview1], "/application.conf")
    .withFallback(ConfigFactory.parseResources(classOf[ActorSystem], "/reference.conf"))
    .resolve()

  // Algorithmia platform is also making it challenging to load classes
  private[this] implicit val actorSystem: ActorSystem = ActorSystem.create("actorsystem", config, classOf[akka.event.DefaultLoggingFilter].getClassLoader)
  private[this] val log = actorSystem.log
  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()


  def apply(rootUri: String): String = {

    val resultFuture = crawlAndSumWithUri(rootUri)
      .andThen {
        case _ => actorSystem.terminate()
      }

    val sum = Await.result(resultFuture, requestTimeout)
    s"The sum of rewards is: $sum"

//    val clOnLogFilter = classOf[DefaultLoggingFilter].getClassLoader
//    val clActorSystem = classOf[ActorSystem].getClassLoader
//    val myCl = classOf[Interview1].getClassLoader
//    val parentCl = myCl.getParent
//    val threadCl = Thread.currentThread.getContextClassLoader
//    //    val rootCl = Package.getPackage("/").getClass.getClassLoader
//
//    def printParents(classLoader: ClassLoader): String = classLoader match {
//
//      case cl: ClassLoader => cl.toString + ", " + printParents(cl.getParent)
//
//      case _ => ""
//    }
//
//    val asClass = myCl.loadClass("akka.actor.ActorSystem")
//    val defaultLoggerClass = myCl.loadClass("akka.event.DefaultLoggingFilter")
//
//    s"""
//       |Class loader for default logging filter: ${printParents(clOnLogFilter)}
//       |Class loader for actor system: ${printParents(clActorSystem)}
//       |Class loader for this class: ${printParents(myCl)}
//       |Class loader for parent: ${printParents(parentCl)}
//       |Class loader for thread: ${printParents(threadCl)}
//       | Actor System class: $asClass
//       | Default logger class: $defaultLoggerClass
//     """.stripMargin
  }

  def crawlAndSumWithUri(rootUri: String): Future[Double] = {

    val initialState = (Set.empty[String], List(rootUri))

    val source = Source.unfoldAsync(initialState) { case (fetched, toFetch) =>

      val fetchable = toFetch
        .filterNot {
          fetched.contains
        }

      fetchable match {

        case fetchUri :: remainingToFetch =>

          log.info(s"Sending HTTP request to URI: $fetchUri")
          val parsedFuture = Http()
            .singleRequest(HttpRequest(uri = fetchUri))
            .flatMap {
              _.entity
                .toStrict(requestTimeout)
                .map { utf8Entity =>
                  ujson.read(Transformable.fromString(utf8Entity.data.utf8String))
                }
            }
            .map(parseRequestFromJson)

          parsedFuture
            .map { case UriResult(reward, childUrls) =>
              val newState = (fetched + fetchUri, remainingToFetch ++ childUrls)
              Some(newState, reward)
            }

        case Nil => Future.successful(None) // No more elements left to process, we're done streaming
      }
    }

    source.runWith(Sink.fold(0.0) { case (tally, reward) =>
      tally + reward
    })
  }

  def parseRequestFromJson(json: Js.Value): UriResult = {

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

    UriResult(json("reward").num, childUris)
  }

}

case class UriResult(reward: Double, childUrls: Seq[String])