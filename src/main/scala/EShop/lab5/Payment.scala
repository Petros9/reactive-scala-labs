package EShop.lab5

import EShop.lab2.TypedCheckout
import EShop.lab3.OrderManager
import EShop.lab3.OrderManager.ConfirmPaymentReceived
import EShop.lab5.Payment.{PaymentRejected, WrappedPaymentServiceResponse}
import EShop.lab5.PaymentService.{PaymentClientError, PaymentServerError, PaymentSucceeded}
import akka.actor.typed.{ActorRef, Behavior, ChildFailed, SupervisorStrategy}
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.StreamTcpException

import scala.concurrent.duration._
import akka.actor.typed.Terminated

object Payment {
  sealed trait Message
  case object DoPayment                                                       extends Message
  case class WrappedPaymentServiceResponse(response: PaymentService.Response) extends Message
  case object PaymentServiceTerminated                                        extends Message

  sealed trait Response
  case object PaymentRejected extends Response

  val restartStrategy = SupervisorStrategy.restart
    .withLimit(maxNrOfRetries = 3, withinTimeRange = 1.second)

  def apply(
    method: String,
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Behavior[Message] =
    Behaviors
      .receive[Message](
        (context, msg) =>
          msg match {
            case DoPayment =>
              val adapter = context.messageAdapter[PaymentService.Response] {
                case response @ PaymentSucceeded =>
                  WrappedPaymentServiceResponse(response)
              }
              val paymentService = Behaviors
                .supervise(PaymentService(method, adapter))
                .onFailure(restartStrategy)
              val paymentServiceRef = context.spawnAnonymous(paymentService)
              context.watchWith(paymentServiceRef, PaymentServiceTerminated)

              Behaviors.same

            case WrappedPaymentServiceResponse(PaymentSucceeded) =>
              println("payment succeeded")
              orderManager ! ConfirmPaymentReceived
              Behaviors.same

            case PaymentServiceTerminated =>
              println("payment terminated")
              notifyAboutRejection(orderManager, checkout)
              Behaviors.same
        }
      )

  // please use this one to notify when supervised actor was stoped
  private def notifyAboutRejection(
    orderManager: ActorRef[OrderManager.Command],
    checkout: ActorRef[TypedCheckout.Command]
  ): Unit = {
    orderManager ! OrderManager.PaymentRejected
    checkout ! TypedCheckout.PaymentRejected
  }

}
