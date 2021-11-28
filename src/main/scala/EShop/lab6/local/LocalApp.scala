package EShop.lab6.local

import EShop.lab5.{ProductCatalog, ProductCatalogJsonSupport, SearchService}
import akka.actor.typed.scaladsl.AskPattern.{schedulerFromActorSystem, Askable}
import akka.actor.typed.scaladsl.{Behaviors, Routers}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.Timeout

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.io.StdIn
import scala.util.Try

object LocalApp extends App {
  val workHttpServer = new LocalHttpApp()
  workHttpServer.run(Try(args(0).toInt).getOrElse(9000))
}

/**
  * The server that distributes all of the requests to the local workers spawned via router pool.
  */
class LocalApp extends ProductCatalogJsonSupport {
  implicit val system: ActorSystem[Nothing] =
    ActorSystem(Behaviors.empty, "LocalRouters")
  implicit val executionContext: ExecutionContextExecutor =
    system.executionContext
  val workers: ActorRef[ProductCatalog.Query] = {
    val searchService = new SearchService()
    system.systemActorOf(Routers.pool(3)(ProductCatalog(searchService)),
                         "workersRouter")
  }

  implicit val timeout: Timeout = 5.seconds

  def routes: Route = {
    path("products") {
      get {
        parameters(Symbol("keywords").as[String].repeated) { keywords =>
          parameter(Symbol("brand").as[String].withDefault("gerber")) { brand =>
            complete {
              system.log.info(
                s"/products, brand: ${brand}, keywords: ${keywords}")

              val items: Future[ProductCatalog.Items] =
                workers
                  .ask(ref =>
                    ProductCatalog.GetItems(brand, keywords.toList, ref))
                  .mapTo[ProductCatalog.Items]

              items
            }
          }
        }
      }
    }
  }

  def run(port: Int): Unit = {
    val bindingFuture = Http().newServerAt("localhost", port).bind(routes)
    println(
      s"Server now online. Please navigate to http://localhost:8080/hello\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}
