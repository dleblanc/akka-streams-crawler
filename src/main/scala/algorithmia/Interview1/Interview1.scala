package algorithmia.Interview1

import com.algorithmia._
import com.algorithmia.algo._
import com.algorithmia.data._
import com.google.gson._

class Interview1 {
  def apply(input: String): String = {

    val munged = input
      .split(" ")
      .map { _.toUpperCase() }
      .mkString(", ")

    "Hello " + munged
  }
}
