package algorithmia.Interview1

import org.scalatest._

class Interview1Spec extends FlatSpec with Matchers {

  "Initial Interview1 algorithm" should "return Hello plus input munged" in {
    val algorithm = new Interview1()
    "Hello BOB, BAR" shouldEqual algorithm.apply("Bob bar")
  }
}
