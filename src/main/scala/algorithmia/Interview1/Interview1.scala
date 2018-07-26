package algorithmia.Interview1

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}

/**
  * Please see StreamBasedCrawler for the up to date implementation (and ActorBasedCrawler for the first one).
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