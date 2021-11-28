package EShop.lab5

import akka.Done
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.json.{
  DefaultJsonProtocol,
  JsString,
  JsValue,
  JsonFormat,
  RootJsonFormat
}

import java.net.URI
import scala.concurrent.duration.{Duration, DurationInt}
import scala.concurrent.{Await, Future}

trait ProductCatalogJsonSupport
    extends SprayJsonSupport
    with DefaultJsonProtocol {
  implicit val uriFormat = new JsonFormat[java.net.URI] {
    override def write(obj: java.net.URI): spray.json.JsValue =
      JsString(obj.toString)
    override def read(json: JsValue): URI =
      json match {
        case JsString(url) => new URI(url)
        case _             => throw new RuntimeException("Parsing exception")
      }
  }

  implicit val itemFormat: RootJsonFormat[ProductCatalog.Item] = jsonFormat5(
    ProductCatalog.Item)
  implicit val itemsFormat: RootJsonFormat[ProductCatalog.Items] = jsonFormat1(
    ProductCatalog.Items)
}

case class ProductCatalogHttpServer(ref: ActorRef[ProductCatalog.Query])(
    implicit val scheduler: Scheduler)
    extends ProductCatalogJsonSupport {
  implicit val timeout: Timeout = 3.second

  def routes: Route = {
    path("products") {
      get {
        parameters(Symbol("keywords").as[String].repeated) { keywords =>
          parameter(Symbol("brand").as[String].withDefault("gerber")) { brand =>
            complete {
              val items =
                ref
                  .ask(ref =>
                    ProductCatalog.GetItems(brand, keywords.toList, ref))
                  .mapTo[ProductCatalog.Items]
              Future.successful(items)
            }
          }
        }
      }
    }
  }
}
object ProductCatalogHttpServerConfig {
  val host = "localhost"
  val port = 9000
}
object ProductCatalogHttpServer {
  def apply(): Behavior[Receptionist.Listing] = {
    Behaviors.setup { context =>
      implicit val system: ActorSystem[Nothing] = context.system

      system.receptionist ! Receptionist.subscribe(
        ProductCatalog.ProductCatalogServiceKey,
        context.self)
      Behaviors.receiveMessage[Receptionist.Listing] { message =>
        val listing =
          message.serviceInstances(ProductCatalog.ProductCatalogServiceKey)
        if (listing.nonEmpty) {
          val ref = listing.head
          val rest = ProductCatalogHttpServer(ref)
          val binding = Http()
            .newServerAt(ProductCatalogHttpServerConfig.host,
                         ProductCatalogHttpServerConfig.port)
            .bind(rest.routes)
          Await.ready(binding, Duration.Inf)
          Behaviors.empty
        } else {
          Behaviors.same
        }
      }
    }
  }

  def start(): Future[Done] = {
    val system = ActorSystem[Receptionist.Listing](ProductCatalogHttpServer(),
                                                   "ProductCatalog")
    val config = ConfigFactory.load()

    val productCatalogSystem = ActorSystem[Nothing](
      Behaviors.empty,
      "ProductCatalog",
      config.getConfig("productcatalog").withFallback(config)
    )

    productCatalogSystem.systemActorOf(
      ProductCatalog(new SearchService()),
      "productcatalog"
    )

    Await.ready(system.whenTerminated, Duration.Inf)
  }
}

object ProductCatalogHttpServerApp extends App {
  ProductCatalogHttpServer.start()
}
