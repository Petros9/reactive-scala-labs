package EShop.lab6.sub

import akka.actor.typed.pubsub.Topic
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.ConfigFactory
import org.slf4j.Logger

import scala.concurrent.Await
import scala.concurrent.duration.Duration

sealed trait RequestCounterTopicMessage
case object ProductsEndpointHitMessage extends RequestCounterTopicMessage

sealed trait RequestCounterCommand
case class RequestProductsEndpointHitsCount(replyTo: ActorRef[Int])
    extends RequestCounterCommand
case object ProductsEndpointHit extends RequestCounterCommand

object RequestCounterApp extends App {
  private val config = ConfigFactory.load()

  val system = ActorSystem[RequestCounterCommand](
    RequestCounter(),
    "ClusterWorkRouters",
    config.getConfig("cluster-default")
  )

  Await.ready(system.whenTerminated, Duration.Inf)
}

// here we need to spawn request counter actor
// register it with a key
// and subscribe for statistics.
object RequestCounter {
  val RequestCounterServiceKey =
    ServiceKey[RequestCounterCommand]("RequestCounter")

  def apply(): Behavior[RequestCounterCommand] = Behaviors.setup { context =>
    context.system.receptionist ! Receptionist
      .register(RequestCounterServiceKey, context.self)
    val topic = context.spawn(RequestCounterTopic(), "RequestCounterTopic")
    val adapter = context.messageAdapter[RequestCounterTopicMessage] {
      case ProductsEndpointHitMessage =>
        ProductsEndpointHit
    }

    topic ! Topic.Subscribe(adapter)
    countRequests(0, context.log)
  }

  def countRequests(state: Int, log: Logger): Behavior[RequestCounterCommand] =
    Behaviors.receiveMessage {
      case ProductsEndpointHit =>
        log.info("Received EndpointHit.")
        countRequests(state + 1, log)
      case RequestProductsEndpointHitsCount(replyTo) =>
        log.info("Received request for endpoint hits count.")
        replyTo ! state
        Behaviors.same
    }

}

object RequestCounterTopic {
  def apply(): Behavior[Topic.Command[RequestCounterTopicMessage]] =
    Topic[RequestCounterTopicMessage]("request-counter")
}
