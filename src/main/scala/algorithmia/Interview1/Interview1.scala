package algorithmia.Interview1

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

/**
  * This is the entry point into the Algorithmia exercise.
  *
  * Please see StreamBasedCrawler for the up to date implementation (and ActorBasedCrawler for the first one).
  *
  * This simply starts an actor system and returns the result of crawling the given URI.
  */
class Interview1 {

  import Interview1._

  def apply(rootUri: String): String = {

    new StreamBasedCrawler().apply(rootUri)
  }

}


object Interview1 {
  val akkaClassLoader: ClassLoader = classOf[akka.event.DefaultLoggingFilter].getClassLoader

  // Akka seems to be having trouble with our classloader
  val config: Config = ConfigFactory
    .defaultApplication(akkaClassLoader)
    .resolve()

  // NOTE: We leave the actor system running for subsequent requests
  implicit val actorSystem: ActorSystem = ActorSystem.create("actorsystem", config, akkaClassLoader)

}