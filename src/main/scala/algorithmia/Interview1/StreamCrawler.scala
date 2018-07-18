package algorithmia.Interview1

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.config.ConfigFactory
import ujson.Transformable

import scala.annotation.tailrec
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContext, Future, Promise}
import scala.util.{Success, Try}

// Being lazy about defining execution contexts, this works fine for this purpose
import scala.concurrent.ExecutionContext.Implicits.global

case class RewardAndChildren(reward: Double, childUrls: Seq[String])

object StreamCrawler {

  // Visited, to visit, pending futures
  type State = (Set[String], List[String], Seq[Future[RewardAndChildren]])
  type StateAndReward = (State, Double)

  def main(args: Array[String]): Unit = {

    val uri = args.headOption.getOrElse("http://algo.work/interview/a")
    println(new StreamCrawler().apply(uri))
  }
}

class StreamCrawler {

  import StreamCrawler._

  private[this] val requestTimeout = 1.minute
  private[this] val sumTimeout = 2.minutes
  private[this] val akkaClassLoader = classOf[akka.event.DefaultLoggingFilter].getClassLoader

  // Akka seems to be having trouble with our classloader
  private[this] val config = ConfigFactory
    .defaultApplication(akkaClassLoader)
    .resolve()

  private[this] implicit val actorSystem: ActorSystem = ActorSystem.create("actorsystem", config, akkaClassLoader)
  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()
  private[this] val log = actorSystem.log

  def apply(rootUri: String): String = {

    val resultFuture = crawlAndSumWithUri(rootUri)
      .andThen {
        case _ => actorSystem.terminate()
      }

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