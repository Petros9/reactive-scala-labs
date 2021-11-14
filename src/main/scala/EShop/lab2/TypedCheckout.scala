package EShop.lab2

import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior, scaladsl}
import EShop.lab3.{OrderManager, Payment}
import scalaz.Scalaz.ToOptionIdOps

import scala.concurrent.duration._
import scala.language.postfixOps
object TypedCheckout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                       extends Command
  case class SelectDeliveryMethod(method: String)                                                 extends Command
  case object CancelCheckout                                                                      extends Command
  case object ExpireCheckout                                                                      extends Command
  case class SelectPayment(payment: String, orderManagerCheckoutRef: ActorRef[Event],
                           orderManagerPaymentRef: ActorRef[Payment.Event])                  extends Command
  case object ExpirePayment                                                                       extends Command
  case object ConfirmPaymentReceived                                                              extends Command

  sealed trait Event
  case object CheckOutClosed                           extends Event
  case class PaymentStarted(paymentRef: ActorRef[Payment.Command]) extends Event
  case object CheckoutStarted                                        extends Event
  case object CheckoutCancelled                                      extends Event
  case class DeliveryMethodSelected(method: String)                  extends Event

  sealed abstract class State(val timerOpt: Option[Cancellable])
  case object WaitingForStart                           extends State(None)
  case class SelectingDelivery(timer: Cancellable)      extends State(timer.some)
  case class SelectingPaymentMethod(timer: Cancellable) extends State(timer.some)
  case object Closed                                    extends State(None)
  case object Cancelled                                 extends State(None)
  case class ProcessingPayment(timer: Cancellable)      extends State(timer.some)
}

class TypedCheckout(
                     cartActor: ActorRef[TypedCartActor.Command]
                   ) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case StartCheckout => selectingDelivery(context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout))
      case _ => Behaviors.same
    }
  }

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive { (context, message) =>
    message match {
      case SelectDeliveryMethod(_) => selectingPaymentMethod(timer)
      case ExpireCheckout => cancelled
      case CancelCheckout => timer.cancel()
        cancelled
      case _ => Behaviors.same
    }
  }

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (context, SelectPayment(payment, orderManagerCheckoutRef, orderManagerPaymentRef)) => timer.cancel()
      orderManagerCheckoutRef ! PaymentStarted(
        context.spawn(new Payment(payment, orderManagerPaymentRef, context.self).start,
          "PaymentActor"))
      processingPayment(context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment))
    case (_, ExpireCheckout) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (_, _) => Behaviors.same
  }

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, ConfirmPaymentReceived) => timer.cancel()
      cartActor ! TypedCartActor.ConfirmCheckoutClosed
      closed
    case (_, ExpirePayment) => cancelled
    case (_, CancelCheckout) => timer.cancel()
      cancelled
    case (_, _) => Behaviors.same
  }

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, _) => Behaviors.same
  }

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive {
    case (_, _) => Behaviors.same
  }

}