package algorithmia.Interview1

object LocalRunner {

  // Provided to make local manual testing easier
  def main(args: Array[String]): Unit = {

    val uri = args.headOption.getOrElse("http://algo.work/interview/a")
    println(new Interview1().apply(uri))
  }
}