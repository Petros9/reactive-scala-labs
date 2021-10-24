package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab2.TypedCheckout.ConfirmPaymentReceived
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command

}

class Payment(
               method: String,
               orderManager: ActorRef[OrderManager.Command],
               checkout: ActorRef[TypedCheckout.Command]
             ) {

  import Payment._

  def start: Behavior[Payment.Command] =  Behaviors.receive[Command] {
    (_, message) =>
      message match {
        case DoPayment => {
          checkout ! TypedCheckout.ConfirmPaymentReceived
          orderManager ! OrderManager.ConfirmPaymentReceived
          Behaviors.stopped
        }
        case _ =>
          Behaviors.same
      }
  }

}