package EShop.lab4

import EShop.lab2.{Cart, TypedCheckout}
import EShop.lab3.OrderManager
import akka.actor.Cancellable
import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

import java.time.Instant
import java.time.temporal.ChronoUnit
import scala.concurrent.duration._

class PersistentCartActor {

  import EShop.lab2.TypedCartActor._

  val cartTimerDuration: FiniteDuration = 5.seconds

  private def scheduleTimer(context: ActorContext[Command]): Cancellable =
    context.scheduleOnce(cartTimerDuration, context.self, ExpireCart)

  def apply(persistenceId: PersistenceId): Behavior[Command] = Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      persistenceId,
      Empty,
      commandHandler(context),
      eventHandler(context)
    ).receiveSignal {
      case (state, PostStop) =>
        state.timerOpt.foreach(_.cancel)
    }
  }

  def commandHandler(context: ActorContext[Command]): (State, Command) => Effect[Event, State] = (state, command) => {
    state match {
      case Empty =>
        command match {
          case AddItem(item) => Effect.persist(ItemAdded(item, Some(Instant.now())))
          case GetItems(sender) =>
            sender ! Cart.empty
            Effect.none
          case _ => Effect.none
        }

      case NonEmpty(cart, _) =>
        command match {
          case AddItem(item)                            => Effect.persist(ItemAdded(item, None))
          case RemoveItem(item) if !cart.contains(item) => Effect.none
          case RemoveItem(_) if cart.size == 1          => Effect.persist(CartEmptied)
          case RemoveItem(item)                         => Effect.persist(ItemRemoved(item))
          case StartCheckout(orderManagerRef) =>
            val checkoutActor = context.spawn(new TypedCheckout(context.self).start, "CheckoutActor")
            Effect.persist(CheckoutStarted(checkoutActor)).thenRun { _ =>
              checkoutActor ! TypedCheckout.StartCheckout
              orderManagerRef ! CheckoutStarted(checkoutActor)
            }
          case ExpireCart => Effect.persist(CartExpired)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case _ => Effect.none
        }

      case InCheckout(cart) =>
        command match {
          case ConfirmCheckoutCancelled => Effect.persist(CheckoutCancelled)
          case ConfirmCheckoutClosed    => Effect.persist(CheckoutClosed)
          case GetItems(sender) =>
            sender ! cart
            Effect.none
          case _ => Effect.none
        }
    }
  }

  def eventHandler(context: ActorContext[Command]): (State, Event) => State = (state, event) => {
    val cart                                        = state.cart
    lazy val timer                                  = state.timerOpt.get
    lazy val stopTimer: Unit                        = state.timerOpt.foreach(_.cancel)

    event match {
      case CheckoutStarted(_) => stopTimer
        InCheckout(cart)
      case ItemAdded(item, Some(_)) => NonEmpty(cart.addItem(item), scheduleTimer(context))
      case ItemAdded(item, _)               => NonEmpty(cart.addItem(item), timer)
      case ItemRemoved(item)                => NonEmpty(cart.removeItem(item), timer)
      case CartEmptied | CartExpired =>
        stopTimer
        Empty
      case CheckoutClosed               => Empty
      case CheckoutCancelled => NonEmpty(cart, scheduleTimer(context))
    }
  }

}
