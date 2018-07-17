package algorithmia.Interview1

import algorithmia.Interview1.Interview1.StateAndReward
import org.scalatest._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}

class Interview1Test extends FunSuite with Matchers {

  val interviewObj = new Interview1()

  test("parse request yields expected UriResult with empty children") {

    val parsed = interviewObj.parseRequestFromJson(
      """{
         "reward": 1.23,
         "children": ["http://a.b/c", "http:d.e/f"]
        }
      """)

    parsed should equal (UriResult(1.23, List("http://a.b/c", "http:d.e/f")))
  }

  test("parse request handles empty children") {

    val parsed = interviewObj.parseRequestFromJson(
      """{
         "reward": 1.23,
         "children": []
        }
      """)

    parsed should equal (UriResult(1.23, Nil))
  }

  test("parse request handles missing children property") {

    val parsed = interviewObj.parseRequestFromJson(
      """{
         "reward": 1.23
        }
      """)

    parsed should equal (UriResult(1.23, Nil))
  }

  test("fetch a single non-nested resource should return the expected state and reward") {

    def fetcher(uri: String): Future[UriResult] = {
      Future.successful(UriResult(1.23, Nil))
    }

    val initialState = (Set.empty[String], List("http://root.uri/"))

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://root.uri/"))
      toVisit should be (empty)
    }
  }

  test("fetch next batch with a nested resource should append the nested resources to the state") {

    def fetcher(uri: String): Future[UriResult] = {
      Future.successful(UriResult(1.23, List("http://a.b", "http://b.c")))
    }

    val initialState = (Set.empty[String], List("http://root.uri/"))

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://root.uri/"))
      toVisit should be (List("http://a.b", "http://b.c"))
    }
  }

  test("fetch next batch with multiple URIs to visit will consume the first") {

    def fetcher(uri: String): Future[UriResult] = {
      Future.successful(UriResult(1.23, Nil))
    }

    val initialState = (Set.empty[String], List("http://first.uri/", "http://second.uri/"))

    val response = fetchSingleBatch(initialState, fetcher)
    response.isDefined should be (true)

    for ((respState, reward) <- response;
         (visited, toVisit) = respState
    ) {
      reward should be (1.23)
      visited should be (Set("http://first.uri/"))
      toVisit should be (List("http://second.uri/"))
    }
  }

  test("fetch next batch skips an already-visited resource") {

    def fetcher(uri: String): Future[UriResult] = {
      Future.successful(UriResult(1.23, Nil))
    }

    val initialState = (Set("http://root.uri/"), List("http://root.uri/"))

    val response = fetchSingleBatch(initialState, fetcher)
    response should be (None)
  }

  def fetchSingleBatch(initialState: Interview1.State, fetcher: String => Future[UriResult]): Option[StateAndReward] = {

    val respFuture = interviewObj.fetchNextBatch(fetcher)(initialState)
    Await.result(respFuture, 5.seconds)
  }
}
