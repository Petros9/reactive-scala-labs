package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab2.TypedCheckout.ConfirmPaymentReceived
import EShop.lab3.Payment.{DoPayment, Event, ReceivedPayment}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command

  sealed trait Event
  case object ReceivedPayment extends Event

}

class Payment(
  method: String,
  orderManager: ActorRef[Event],
  checkout: ActorRef[TypedCheckout.Command]
) {

  def start: Behavior[Payment.Command] = Behaviors.receive { (_, message) =>
    message match {
      case DoPayment =>
        orderManager ! ReceivedPayment
        checkout ! TypedCheckout.ConfirmPaymentReceived
        Behaviors.same
      case _ => Behaviors.same
    }
  }
}
