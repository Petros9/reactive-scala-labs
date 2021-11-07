package EShop.lab3

import EShop.lab2.TypedCheckout
import EShop.lab2.TypedCheckout.ConfirmPaymentReceived
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object Payment {

  sealed trait Command
  case object DoPayment extends Command


  sealed trait Event
  case object PaymentReceived extends Event


  //
  def apply(
             method: String,
             orderManager: ActorRef[Event],
             checkout: ActorRef[Event]
           ): Behavior[Command] =
    Behaviors.setup(_ => new Payment(method, orderManager, checkout).start)

  //
}

class Payment(
               method: String,
               orderManager: ActorRef[Payment.Event],
               checkout: ActorRef[Payment.Event]
             ) {

  import Payment._

  def start: Behavior[Payment.Command] =  Behaviors.receive[Command] {
    (_, message) =>message match {
        case DoPayment => {
          checkout ! PaymentReceived
          orderManager ! PaymentReceived
          Behaviors.stopped
        }
      }
  }

}
