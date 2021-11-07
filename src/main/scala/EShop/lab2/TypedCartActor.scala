package EShop.lab2

import EShop.lab2.TypedCheckout.Command
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, TimerScheduler}
import akka.actor.typed.{ActorRef, Behavior}

import scala.concurrent.duration._
import scala.language.postfixOps

object TypedCartActor {

  sealed trait Command
  case class AddItem(item: Any)                              extends Command
  case class RemoveItem(item: Any)                           extends Command
  case object ExpireCart                                     extends Command
  case class StartCheckout(orderManagerRef: ActorRef[Event]) extends Command
  case object ConfirmCheckoutCancelled                       extends Command
  case object ConfirmCheckoutClosed                          extends Command
  case class GetItems(sender: ActorRef[Cart])                extends Command // command made to make testing easier

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command]) extends Event

  def apply(): Behavior[Command] = Behaviors.setup(context => new TypedCartActor().start)
}

class TypedCartActor {
  import TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5 seconds

  var checkoutMapper: ActorRef[TypedCheckout.Event] = null

  private def scheduleTimer(timers: TimerScheduler[TypedCartActor.Command]) =
    timers.startSingleTimer(ExpireCart, ExpireCart, cartTimerDuration)

  def start: Behavior[TypedCartActor.Command] = Behaviors.setup { context =>
    checkoutMapper = context.messageAdapter(
      event =>
        event match {
          case TypedCheckout.CheckoutClosed    => ConfirmCheckoutClosed
          case TypedCheckout.CheckoutCancelled => ConfirmCheckoutCancelled
        }
    )

    Behaviors.withTimers(timers => empty(timers))
  }

  def empty(timers: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] = Behaviors.receive(
    (context, message) =>
      message match {
        case AddItem(item) =>
          val cart = Cart.empty.addItem(item)
          scheduleTimer(timers)
          nonEmpty(cart, timers)

        case GetItems(sender) =>
          sender ! Cart.empty
          Behaviors.same
      }
  )

  def nonEmpty(cart: Cart, timer: TimerScheduler[TypedCartActor.Command]): Behavior[TypedCartActor.Command] =
    Behaviors.receive(
      (context, message) =>
        message match {
          case AddItem(item) =>
            val updatedCart = cart.addItem(item)
            nonEmpty(updatedCart, timer)

          case RemoveItem(item) =>
            if (cart.contains(item)) {
              val updatedCart = cart.removeItem(item)

              if (updatedCart.size == 0) {
                timer.cancel(ExpireCart)
                empty(timer)
              } else {
                scheduleTimer(timer)
                nonEmpty(updatedCart, timer)
              }
            } else
              Behaviors.same

          case GetItems(sender) =>
            sender ! cart
            Behaviors.same

          case StartCheckout(orderManagerRef) =>
            timer.cancel(ExpireCart)

            val checkoutRef = context.spawn(TypedCheckout(checkoutMapper), "checkout")
            checkoutRef ! TypedCheckout.StartCheckout

            orderManagerRef ! CheckoutStarted(checkoutRef)
            inCheckout(cart, timer)

          case ExpireCart =>
            empty(timer)
        }
    )

  def inCheckout(
                  cart: Cart,
                  timers: TimerScheduler[TypedCartActor.Command]
                ): Behavior[TypedCartActor.Command] =
    Behaviors.receive(
      (_, message) =>
        message match {
          case ConfirmCheckoutClosed =>
            empty(timers)

          case ConfirmCheckoutCancelled =>
            scheduleTimer(timers)
            nonEmpty(cart, timers)
        }
    )
}