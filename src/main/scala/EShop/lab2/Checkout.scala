package EShop.lab2

import EShop.lab2.Checkout._
import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.{Logging, LoggingReceive}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object Checkout {

  sealed trait Data
  case object Uninitialized                               extends Data
  case class SelectingDeliveryStarted(timer: Cancellable) extends Data
  case class ProcessingPaymentStarted(timer: Cancellable) extends Data

  sealed trait Command
  case object StartCheckout                       extends Command
  case class SelectDeliveryMethod(method: String) extends Command
  case object CancelCheckout                      extends Command
  case object ExpireCheckout                      extends Command
  case class SelectPayment(payment: String)       extends Command
  case object ExpirePayment                       extends Command
  case object ConfirmPaymentReceived              extends Command

  sealed trait Event
  case object CheckOutClosed                   extends Event
  case class PaymentStarted(payment: ActorRef) extends Event

}

class Checkout extends Actor {

  private val scheduler = context.system.scheduler
  private val log       = Logging(context.system, this)

  var delivery = ""
  var payment = ""

  private def checkoutTimer: Cancellable =
    scheduler.scheduleOnce(checkoutTimerDuration, self, ExpireCheckout)

  private def paymentTimer: Cancellable =
    scheduler.scheduleOnce(paymentTimerDuration, self, ExpirePayment)

  val checkoutTimerDuration = 5 seconds
  val paymentTimerDuration  = 5 seconds

  def receive: Receive = {
    case StartCheckout =>
      log.info(s"Starting checking out")
      context.become(selectingDelivery(checkoutTimer))
  }

  def selectingDelivery(timer: Cancellable): Receive = {
    case SelectDeliveryMethod(method: String) =>
      log.info(s"Delivery method: $method")
      this.delivery = method
      timer.cancel()
      context.become(selectingPaymentMethod(checkoutTimer))

    case ExpireCheckout =>
      timer.cancel()
      log.info(s"Checkout expired")
      context.become(cancelled)

    case CancelCheckout =>
      log.info(s"Checkout cancelled")
      timer.cancel()
      context.become(cancelled)
  }

  def selectingPaymentMethod(timer: Cancellable): Receive = {
    case SelectPayment(payment: String) =>
      log.info(s"Payment method: $payment")
      this.payment = payment
      timer.cancel()
      context.become(processingPayment(paymentTimer))

    case ExpireCheckout =>
      timer.cancel()
      log.info(s"Checkout expired")
      context.become(cancelled)

    case CancelCheckout =>
      log.info(s"Checkout cancelled")
      timer.cancel()
      context.become(cancelled)
  }

  def processingPayment(timer: Cancellable): Receive = {
    case ConfirmPaymentReceived =>
      timer.cancel()
      log.info(s"Payment received")
      context.become(closed)

    case ExpirePayment =>
      timer.cancel()
      log.info(s"Checkout expired")
      context.become(cancelled)

    case CancelCheckout =>
      timer.cancel()
      log.info(s"Checkout cancelled")
      context.become(cancelled)
  }


  def cancelled: Receive = {
    _ => context.stop(self)
  }

  def closed: Receive = {
    _ =>
      log.info("Closing checkout")
      println("closing")
      context.stop(self)
  }

}
