package EShop.lab6.cluster

import EShop.lab5.{ProductCatalog, SearchService}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object ProductCatalogNodeApp extends App {
  private val config = ConfigFactory.load()
  val workersPerNode = 3

  val system = ActorSystem[Nothing](
    Behaviors.empty,
    "ClusterWorkRouters",
    config
      .getConfig(Try(args(0)).getOrElse("seed-node1"))
      .withFallback(config.getConfig("cluster-default"))
  )

  for (i <- 0 to workersPerNode)
    spawnProductCatalogWorker(system, i)

  Await.ready(system.whenTerminated, Duration.Inf)

  def spawnProductCatalogWorker(system: ActorSystem[Nothing], number: Int): ActorRef[ProductCatalog.Query] = {
    val searchService = new SearchService()
    val workerName    = s"workersRouter$number"
    val worker: ActorRef[ProductCatalog.Query] =
      system.systemActorOf(ProductCatalog(searchService), workerName)

    system.log.info(s"Spawned $workerName.")

    worker
  }
}
