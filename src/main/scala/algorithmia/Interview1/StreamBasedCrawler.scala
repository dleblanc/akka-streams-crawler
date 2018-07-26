package algorithmia.Interview1

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import algorithmia.Interview1.StreamBasedCrawler.{State, StateAndReward}
import com.typesafe.config.{Config, ConfigFactory}
import ujson.Transformable

import scala.annotation.tailrec
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.Try

// Being lazy about defining execution contexts, this works fine for this purpose
import scala.concurrent.ExecutionContext.Implicits.global

/*
 * After getting the actor based crawler working, this is my attempt at a cleaner, more functional approach using Akka
 * Streams.
 *
 * I initialize a (Akka Stream) Source with the root URI (and empty state).
 *
 * We use 'unfoldAsync', which passes a state between calls, and allows us to asynchronously emit items as well as
 * a future which represents the pending calls.
 *
 * At each unfoldAsync step, we:
 *
 *  - get the set of URIs to fetch, removing any duplicates and ones we've already visited (via the state)
 *
 *  - fetch each URI (using Akka HTTP), getting a Future to the eventual result
 *
 *  - combine any pending futures (via the state) and any newly created futures from above, and select on the first
 *      ready one. We then return a future which is the first ready result, and the new state (the other remaining futures,
 *      the lists of URIs to visit, and the fetched URI futures).
 *
 * By using this approach, since our state is immutable and we only process a single ready future at a time, we can simplify the state
 *    management and not worry about concurrency/race issues there. We still benefit from a non-blocking and parallel
 *    approach (since Akka HTTP performs fetching in parallel via its own actors and pooled HTTP connections).
 *
 * All of this provides us a Source that produces RewardAndChildren items in a stream. We simply perform a fold on this,
 * tallying up the rewards into a sum.
 *
 * I've also composed this in such a way that we can independently test the component pieces, in particular the fetchNextBatch
 * function (see StreamBasedCrawlerTest).
 */
class StreamBasedCrawler(implicit val actorSystem: ActorSystem) {

  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()
  private[this] val log: LoggingAdapter = actorSystem.log

  private[this] val requestTimeout = 1.minute
  private[this] val sumTimeout = 2.minutes

  def apply(rootUri: String): String = {

    val resultFuture = crawlAndSumWithUri(rootUri)

    val sum = Await.result(resultFuture, requestTimeout)
    s"The sum of rewards is: $sum"
  }

  def crawlAndSumWithUri(rootUri: String): Future[Double] = {

    val initialState: State = (Set.empty[String], List(rootUri), Nil)

    val source = Source.unfoldAsync(initialState) {
      fetchNextBatch(fetchAndParseFromUri)
    }

    source.runWith(Sink.fold(0.0) { case (tally, reward) =>
      tally + reward
    })
  }

  // Use a curried function so we can supply a different 'fetcher' for the tests
  def fetchNextBatch(fetcher: String => Future[RewardAndChildren])(state: State): Future[Option[StateAndReward]] = {
    val (fetched, toFetch, pendingFutures) = state

    // Filter so we don't re-request duplicate URIs, the reward for a URI is only counted once
    val fetchUris = toFetch
      .toSet // Remove dupes
      .diff(fetched) // And ignore any URIs we've already visited

    // Fetch all URLs in parallel
    val fetchFutures = fetchUris
      .map { uri =>
        log.info("Making a request for URI: " + uri)
        fetcher(uri)
      }

    pendingFutures ++ fetchFutures match {

      case Nil => Future.successful(None) // No more requests to make, signal that the stream is complete

      case allFutures =>

        // Pump the first eventual result out to the stream, and continue the stream processing the rest (and the newly
        // resolved children)
        selectFuture(allFutures)
          .map {
            case (resultTry, otherFutures) =>
              val RewardAndChildren(reward, childUrls) = resultTry.get
              val newState = (fetched ++ fetchUris, childUrls.toList, otherFutures)
              Some((newState, reward))
          }
    }
  }

  def fetchAndParseFromUri(uri: String): Future[RewardAndChildren] = {

    // NOTE: such a simple application of Akka-HTTP I'm omitting the test for it

    Http()
      .singleRequest(HttpRequest(uri = uri))
      .flatMap {
        _.entity
          .toStrict(requestTimeout)
          .map {
            _.data.utf8String
          }
      }
      .map(parseRequestFromJson)

  }

  def parseRequestFromJson(jsonString: String): RewardAndChildren = {

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

    RewardAndChildren(json("reward").num, childUris)
  }

  /**
    * "Select" off the first future to be satisfied.  Return this as a
    * result, with the remainder of the Futures as a sequence.
    *
    * NOTE: This was written by Viktor Klang, posted here: https://gist.github.com/viktorklang/4488970
    *
    * @param fs a scala.collection.Seq
    */
  def selectFuture[A](fs: Seq[Future[A]])(implicit ec: ExecutionContext): Future[(Try[A], Seq[Future[A]])] = {
    @tailrec
    def stripe(p: Promise[(Try[A], Seq[Future[A]])],
               heads: Seq[Future[A]],
               elem: Future[A],
               tail: Seq[Future[A]]): Future[(Try[A], Seq[Future[A]])] = {
      elem onComplete { res => if (!p.isCompleted) p.trySuccess((res, heads ++ tail)) }
      if (tail.isEmpty) p.future
      else stripe(p, heads :+ elem, tail.head, tail.tail)
    }

    if (fs.isEmpty) Future.failed(new IllegalArgumentException("empty future list!"))
    else stripe(Promise(), fs.genericBuilder[Future[A]].result, fs.head, fs.tail)
  }
}

object StreamBasedCrawler {

  // Visited, to visit, pending futures
  type State = (Set[String], List[String], Seq[Future[RewardAndChildren]])
  type StateAndReward = (State, Double)

  // Provide a way to run it locally, outside of Algorithmia
  def main(args: Array[String]): Unit = {

    val akkaClassLoader: ClassLoader = classOf[akka.event.DefaultLoggingFilter].getClassLoader

    // Akka seems to be having trouble with our classloader
    val config: Config = ConfigFactory
      .defaultApplication(akkaClassLoader)
      .resolve()

    implicit val actorSystem: ActorSystem = ActorSystem.create("actorsystem", config, akkaClassLoader)
    val uri = args.headOption.getOrElse("http://algo.work/interview/a")
    val resultFuture = new StreamBasedCrawler().crawlAndSumWithUri(uri)
      .andThen {
        // Shut down the actor system if running locally
        case _ => actorSystem.terminate()
      }

  }
}

case class RewardAndChildren(reward: Double, childUrls: Seq[String])