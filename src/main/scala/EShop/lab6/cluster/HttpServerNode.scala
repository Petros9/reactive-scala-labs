package EShop.lab6.cluster

import EShop.lab5.{ProductCatalog, ProductCatalogJsonSupport}
import EShop.lab6.sub.{RequestCounter, RequestCounterCommand, RequestProductsEndpointHitsCount}
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives.{_symbol2NR, complete, get, parameter, parameters, path}
import akka.http.scaladsl.server.{Directives, Route}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.StdIn

object HttpServerNodeApp extends App {
  val workHttpServerInCluster = new HttpServerNode()
  workHttpServerInCluster.run(args(0).toInt)
}

class HttpServerNode() extends ProductCatalogJsonSupport {
  private val config = ConfigFactory.load()

  implicit val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterWorkRouters",
    config.getConfig("cluster-default")
  )

  implicit val scheduler        = system.scheduler
  implicit val executionContext = system.executionContext

  implicit val timeout: Timeout = 5.seconds

  val workers: ActorRef[ProductCatalog.Query] =
    system.systemActorOf(Routers.group(ProductCatalog.ProductCatalogServiceKey), "clusterWorkerRouter")

  val requestCounter: ActorRef[RequestCounterCommand] =
    system.systemActorOf(Routers.group(RequestCounter.RequestCounterServiceKey), "requestCounterRouter")

  def routes: Route = Directives.concat(
    path("products") {
      get {
        parameters(Symbol("keywords").as[String].repeated) { keywords =>
          parameter(Symbol("brand").as[String].withDefault("gerber")) { brand =>
            complete {
              system.log.info(s"/products, brand: ${brand}, keywords: ${keywords}")

              val items: Future[ProductCatalog.Items] =
                workers
                  .ask(ref => ProductCatalog.GetItems(brand, keywords.toList, ref))
                  .mapTo[ProductCatalog.Items]

              items
            }
          }
        }
      }
    },
    path("metrics") {
      get {
        complete {
          requestCounter
            .ask(ref => RequestProductsEndpointHitsCount(ref))
            .map { hitsCount =>
              s"""{
                 | "/products": $hitsCount
                 |}""".stripMargin
            }
        }
      }
    }
  )

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    println(s"Server now online.\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
  }
}
