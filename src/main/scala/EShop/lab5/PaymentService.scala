package EShop.lab5

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object PaymentService {

  sealed trait Response
  case object PaymentSucceeded extends Response

  case class PaymentClientError() extends Exception("Client Error")
  case class PaymentServerError() extends Exception("Server Error")

  // actor behavior which needs to be supervised
  // use akka.http.scaladsl.Http to make http based payment request
  // use getUri method to obtain url
  def apply(
    method: String,
    payment: ActorRef[Response]
  ): Behavior[HttpResponse] = Behaviors.setup { context =>
    implicit val system: ActorSystem[Nothing] = context.system
    implicit val executionContext: ExecutionContextExecutor =
      system.executionContext

    Http()
      .singleRequest(Get(getURI(method)))
      .onComplete {
        case Success(response)  => context.self ! response
        case Failure(exception) => throw exception
      }

    Behaviors.receiveMessage {
      case HttpResponse(code, _, _, _) if code.isSuccess() =>
        payment ! PaymentSucceeded
        Behaviors.stopped
      case HttpResponse(code, _, _, _) if code.intValue() >= 500 || code.intValue() == 408 =>
        throw PaymentServerError()
      case HttpResponse(code, _, _, _) if code.intValue() >= 400 && code.intValue() < 500 =>
        throw PaymentClientError()

    }
  }

  // remember running PymentServiceServer() before trying payu based payments
  private def getURI(method: String) = method match {
    case "payu"   => "http://127.0.0.1:8080"
    case "paypal" => s"http://httpbin.org/status/408"
    case "visa"   => s"http://httpbin.org/status/200"
    case _        => s"http://httpbin.org/status/404"
  }
}
