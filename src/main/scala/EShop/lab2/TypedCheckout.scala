package EShop.lab2

import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import EShop.lab3.OrderManager.ConfirmPaymentStarted
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import EShop.lab3.{OrderManager, Payment}

import scala.concurrent.duration._
import scala.language.postfixOps
object TypedCheckout {
  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                                                                        extends Command
  case class SelectDeliveryMethod(method: String)                                                  extends Command
  case object CancelCheckout                                                                       extends Command
  case object ExpireCheckout                                                                       extends Command
  case class SelectPayment(payment: String, orderManagerRef: ActorRef[OrderManager.Command])       extends Command
  case object ExpirePayment                                                                        extends Command
  case object ConfirmPaymentReceived                                                               extends Command

  sealed trait Event
  case object CheckOutClosed                        extends Event
  case class PaymentStarted(payment: ActorRef[Any]) extends Event
}

class TypedCheckout(cartActor: ActorRef[TypedCartActor.Command]) {
  import TypedCheckout._

  val checkoutTimerDuration: FiniteDuration = 1 seconds
  val paymentTimerDuration: FiniteDuration  = 1 seconds
  private def scheduleCheckoutTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(checkoutTimerDuration, context.self, ExpireCheckout)
  private def schedulePaymentTimer(context: ActorContext[TypedCheckout.Command]): Cancellable =
    context.scheduleOnce(paymentTimerDuration, context.self, ExpirePayment)

  def start: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
        case StartCheckout =>
          selectingDelivery(scheduleCheckoutTimer(context))
        case _ =>
          Behaviors.same
      }
  )

  def selectingDelivery(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
        case SelectDeliveryMethod(method) =>
          timer.cancel
          selectingPaymentMethod(scheduleCheckoutTimer(context))
        case CancelCheckout =>
          cancelled
        case ExpireCheckout =>
          cancelled
        case _ =>
          Behaviors.same
      }
  )

  def selectingPaymentMethod(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (context, message) => message match {
        case SelectPayment(method, orderManagerRef) =>
          timer.cancel
          val paymentActor = context.spawn(new Payment(method, orderManagerRef, context.self).start, name="payment")
          orderManagerRef ! ConfirmPaymentStarted(paymentActor)
          processingPayment(schedulePaymentTimer(context))
        case CancelCheckout =>
          cancelled
        case ExpireCheckout =>
          cancelled
        case _ =>
          Behaviors.same
      }
  )

  def processingPayment(timer: Cancellable): Behavior[TypedCheckout.Command] = Behaviors.receive(
    (_, message) => message match {
        case ConfirmPaymentReceived =>
          timer.cancel
          cartActor ! ConfirmCheckoutClosed
          closed
        case CancelCheckout =>
          cancelled
        case ExpirePayment =>
          cancelled
        case _ =>
          Behaviors.same
      }
  )

  def cancelled: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (_, message) => message match {
        case _ => Behaviors.stopped
      }
  )

  def closed: Behavior[TypedCheckout.Command] = Behaviors.receive(
    (_, message) => message match {
        case _ => Behaviors.stopped
      }
  )
}