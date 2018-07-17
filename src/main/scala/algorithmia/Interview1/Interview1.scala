package algorithmia.Interview1

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import ujson.{Js, Transformable}

import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, Future}

// Being lazy about defining execution contexts, this works fine for this purpose
import scala.concurrent.ExecutionContext.Implicits.global

object Interview1 {
  type State = (Set[String], List[String])
  type StateAndReward = (State, Double)
}

class Interview1 {
  import Interview1._

  private[this] val requestTimeout = 1.minute
  private[this] val sumTimeout = 2.minutes

  // Akka doesn't like how Algorithmia packages up the resources - provide a classloader to help it out
  private[this] val config =  ConfigFactory
    .parseResources(classOf[Interview1], "/application.conf")
    .withFallback(ConfigFactory.parseResources(classOf[ActorSystem], "/reference.conf"))
    .resolve()

  // Akka's default classloader mechanism fails to use the correct classloader - be explicit here
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
  }

  def crawlAndSumWithUri(rootUri: String): Future[Double] = {

    val initialState = (Set.empty[String], List(rootUri))

    val source = Source.unfoldAsync(initialState) { fetchNextBatch(fetchAndParseFromUri) }

    source.runWith(Sink.fold(0.0) { case (tally, reward) =>
      tally + reward
    })
  }

  // Use a curried function so we can supply a different 'fetcher' for the tests
  def fetchNextBatch(fetcher: String => Future[UriResult])(state: State): Future[Option[StateAndReward]] = {
    val (fetched, toFetch) = state

    // Filter so we don't re-request duplicate URIs, the reward for a URI is only counted once
    val fetchable = toFetch
      .filterNot {
        fetched.contains
      }

    fetchable match {

      case fetchUri :: remainingToFetch =>

        log.info(s"Sending HTTP request to URI: $fetchUri")

        fetcher(fetchUri)
          .map { case UriResult(reward, childUrls) =>
            val newState = (fetched + fetchUri, remainingToFetch ++ childUrls)
            Some(newState, reward)
          }

      case Nil => Future.successful(None) // No more elements left to process, we're done streaming
    }
  }

  def fetchAndParseFromUri(uri: String): Future[UriResult] = {

    // NOTE: such a simple application of Akka-HTTP I'm omitting the test for it

    Http()
      .singleRequest(HttpRequest(uri = uri))
      .flatMap {
        _.entity
          .toStrict(requestTimeout)
          .map { _.data.utf8String}
      }
      .map(parseRequestFromJson)

  }

  def parseRequestFromJson(jsonString: String): UriResult = {

    val json = ujson.read(Transformable.fromString(jsonString))

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