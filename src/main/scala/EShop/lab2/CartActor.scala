package EShop.lab2

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.event.Logging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object CartActor {

  sealed trait Command
  case class AddItem(item: Any)        extends Command
  case class RemoveItem(item: Any)     extends Command
  case object ExpireCart               extends Command
  case object StartCheckout            extends Command
  case object ConfirmCheckoutCancelled extends Command
  case object ConfirmCheckoutClosed    extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  def props = Props(new CartActor())
}

class CartActor extends Actor {

  import CartActor._

  private val log       = Logging(context.system, this)
  val cartTimerDuration = 10 seconds

  private def scheduleTimer: Cancellable =
    context.system.scheduler.scheduleOnce(cartTimerDuration, self, ExpireCart)

  def printCart(cart: Cart): Unit = cart.list

  def receive: Receive = empty

  def empty: Receive = {
    case AddItem(item) =>
      log.info(s"Adding $item")
      context.become(nonEmpty(Cart.empty.addItem(item), scheduleTimer))
  }

  def nonEmpty(cart: Cart, timer: Cancellable): Receive = {
    case AddItem(item) =>
      log.info(s"Adding $item")
      timer.cancel()
      cart.list
      context.become(nonEmpty(cart.addItem(item), scheduleTimer))

    case RemoveItem(item) if cart.contains(item) && cart.size == 1 =>
      timer.cancel()
      cart.list
      log.info(s"Deleting $item")
      if (cart.size > 1)
        context.become(nonEmpty(cart.removeItem(item), scheduleTimer))
      if (cart.size == 1 && cart.contains(item)) context.become(empty)

    case StartCheckout =>
      timer.cancel()
      cart.list
      log.info(s"Starting checking out")
      context.become(inCheckout(cart))

    case ExpireCart =>
      timer.cancel()
      log.info(s"Cart expired")
      context.become(empty)

  }

  def inCheckout(cart: Cart): Receive = {
    case ConfirmCheckoutCancelled =>
      log.info(s"Checkout cancelled")
      context.become(nonEmpty(cart, scheduleTimer))

    case ConfirmCheckoutClosed =>
      log.info(s"Checkout closed")
      context.become(empty)
  }

}
