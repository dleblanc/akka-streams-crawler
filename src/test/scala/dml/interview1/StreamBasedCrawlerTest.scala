package dml.interview1

import akka.actor.ActorSystem
import akka.testkit.TestKit
import dml.interview1.StreamBasedCrawler.{State, StateAndReward}
import org.scalatest._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class StreamBasedCrawlerTest extends TestKit(ActorSystem()) with FunSuiteLike with Matchers {

  val crawler = new StreamBasedCrawler()

  test("parse request yields expected RewardAndChildren with empty children") {

    val parsed = crawler.parseRequestFromJson(
      """{
         "reward": 1.23,
         "children": ["http://a.b/c", "http:d.e/f"]
        }
      """)

    parsed should equal (RewardAndChildren(1.23, List("http://a.b/c", "http:d.e/f")))
  }

  test("parse request handles empty children") {

    val parsed = crawler.parseRequestFromJson(
      """{
         "reward": 1.23,
         "children": []
        }
      """)

    parsed should equal (RewardAndChildren(1.23, Nil))
  }

  test("parse request handles missing children property") {

    val parsed = crawler.parseRequestFromJson(
      """{
         "reward": 1.23
        }
      """)

    parsed should equal (RewardAndChildren(1.23, Nil))
  }

  test("fetch a single non-nested resource should return the expected state and reward") {

    def fetcher(uri: String): Future[RewardAndChildren] = {
      Future.successful(RewardAndChildren(1.23, Nil))
    }

    val initialState = (Set.empty[String], List("http://root.uri/"), Nil)

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit, pendingFutures) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://root.uri/"))
      toVisit should be (empty)
      pendingFutures should be (empty)
    }
  }

  test("fetch next batch with a nested resource should append the nested resources to the state") {

    def fetcher(uri: String): Future[RewardAndChildren] = {
      Future.successful(RewardAndChildren(1.23, List("http://a.b", "http://b.c")))
    }

    val initialState = (Set.empty[String], List("http://root.uri/"), Nil)

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit, _) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://root.uri/"))
      toVisit should be (List("http://a.b", "http://b.c"))
    }
  }

  test("fetch next batch with multiple URIs to visit will visit them all at once") {

    def fetcher(uri: String): Future[RewardAndChildren] = {
      Future.successful(RewardAndChildren(1.23, Nil))
    }

    val initialState = (Set.empty[String], List("http://first.uri/", "http://second.uri/"), Nil)

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit, _) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://first.uri/", "http://second.uri/"))
      toVisit should be (empty)
    }
  }

  test("fetch next batch skips an already-visited resource") {

    def fetcher(uri: String): Future[RewardAndChildren] = {
      Future.successful(RewardAndChildren(1.23, Nil))
    }

    val initialState = (Set("http://root.uri/"), List("http://root.uri/"), Nil)

    val response = fetchSingleBatch(initialState, fetcher)
    response should be (None)
  }

  def fetchSingleBatch(initialState: State, fetcher: String => Future[RewardAndChildren]): Option[StateAndReward] = {

    val respFuture = crawler.fetchNextBatch(fetcher)(initialState)
    Await.result(respFuture, 5.seconds)
  }
}
