package EShop.lab2

import EShop.lab2.TypedCheckout.Command
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                                             extends Command
  case class RemoveItem(item: Any)                                          extends Command
  case object ExpireCart                                                    extends Command
  case class StartCheckout(orderManagerRef: ActorRef[OrderManager.Command]) extends Command
  case object ConfirmCheckoutCancelled                                      extends Command
  case object ConfirmCheckoutClosed                                         extends Command
  case class GetItems(sender: ActorRef[Cart])                               extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

}

class TypedCartActor {

  import TypedCartActor._
  val cartTimerDuration: FiniteDuration = 5 seconds
  private def scheduleTimer(context: ActorContext[TypedCartActor.Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def start: Behavior[TypedCartActor.Command] = empty

  def empty: Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) =>
      message match {
        case AddItem(item) =>
          nonEmpty(Cart.empty.addItem(item), scheduleTimer(context))
        case GetItems(sender: ActorRef[Cart]) =>
          sender ! Cart.empty
          Behaviors.same
        case _ =>
          Behaviors.same
      }
  )

  def nonEmpty(cart: Cart, timer: Cancellable): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) =>
      message match {
        case AddItem(item) =>
          timer.cancel
          nonEmpty(cart.addItem(item), scheduleTimer(context))
        case RemoveItem(item) if cart contains item =>
          if (cart.size == 1) {
            timer.cancel
            empty
          }
          else {
            timer.cancel
            nonEmpty(cart.removeItem(item), scheduleTimer(context))
          }
        case ExpireCart =>
          timer.cancel
          empty
        case StartCheckout(orderManagerRef) =>
          timer.cancel
          val checkoutActor: ActorRef[TypedCheckout.Command] = context.spawn(new TypedCheckout(context.self).start,"checkout")
          checkoutActor ! TypedCheckout.StartCheckout
          orderManagerRef ! OrderManager.ConfirmCheckoutStarted(checkoutActor)
          inCheckout(cart)
        case GetItems(sender: ActorRef[Cart]) =>
          sender ! cart
          Behaviors.same
        case _ =>
          Behaviors.same
      }
  )

  def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) =>
      message match {
        case ConfirmCheckoutCancelled =>
          nonEmpty(cart, scheduleTimer(context))
        case ConfirmCheckoutClosed =>
          empty
        case _ =>
          Behaviors.same
      }
  )
}