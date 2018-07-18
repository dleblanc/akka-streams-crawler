package algorithmia.Interview1

import java.util.concurrent.ConcurrentHashMap

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.concurrent.duration.DurationDouble
import scala.concurrent.{Await, ExecutionContextExecutor}


class Interview1 {

  private[this] val sumTimeout = 3.minutes

  private val akkaClassLoader = classOf[akka.event.DefaultLoggingFilter].getClassLoader

  // Akka seems to be having trouble with our classloader
  private[this] val config = ConfigFactory
    .defaultApplication(akkaClassLoader)
    .resolve()

  private[this] implicit val actorSystem: ActorSystem = ActorSystem.create("actorsystem", config, akkaClassLoader)
  private[this] implicit val materializer: ActorMaterializer = ActorMaterializer()
  private[this] val log = actorSystem.log


  def apply(rootUri: String): String = {

    val sharedFetchUrlMap = new ConcurrentHashMap[String, Unit]().asScala
    val crawlerActor = actorSystem.actorOf(Props(new CrawlerActor(sharedFetchUrlMap)))

    implicit val timeout: Timeout = sumTimeout
    val future = (crawlerActor ? ResourceRequest(rootUri)).mapTo[Option[UriResult]]

    implicit val dispatcher: ExecutionContextExecutor = actorSystem.dispatcher
    val recoveredFuture = future
      .filter { _.isDefined }
      .map { _.get.flatten() }
      .recover {
        case ex =>
          log.error(ex, "Error occurred: ")
      }
      .andThen { case _ => actorSystem.terminate() }
      .mapTo[List[UriResult]]

    val sumFuture = recoveredFuture.map {
      _.map { _.reward }
      .sum
    }

    val sum = Await.result(sumFuture, timeout.duration)

    s"The sum of rewards is: $sum"
  }

}
