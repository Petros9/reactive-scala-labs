package EShop.lab3

import EShop.lab2.TypedCartActor.StartCheckout
import EShop.lab2.{TypedCartActor, TypedCheckout}
import EShop.lab3.Payment.DoPayment
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}

object OrderManager {

  sealed trait Command
  case class AddItem(id: String, sender: ActorRef[Ack])                                               extends Command
  case class RemoveItem(id: String, sender: ActorRef[Ack])                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: String, payment: String, sender: ActorRef[Ack]) extends Command
  case class Buy(sender: ActorRef[Ack])                                                               extends Command
  case class Pay(sender: ActorRef[Ack])                                                               extends Command
  case class ConfirmCheckoutStarted(checkoutRef: ActorRef[TypedCheckout.Command])                     extends Command
  case class ConfirmPaymentStarted(paymentRef: ActorRef[Payment.Command])                             extends Command
  case object ConfirmPaymentReceived                                                                  extends Command
  case object PaymentRejected                                                                         extends Command
  case object PaymentRestarted                                                                        extends Command

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  sealed trait Event

  def apply(): Behavior[OrderManager.Command] = Behaviors.setup { context =>
    new OrderManager(context).start
  }
}

class OrderManager(context: ActorContext[OrderManager.Command]) {

  import OrderManager._

  val cartEventMapper: ActorRef[TypedCartActor.Event] =
    context.messageAdapter {
      case TypedCartActor.CheckoutStarted(checkoutRef) =>
        ConfirmCheckoutStarted(checkoutRef)
    }

  val checkoutEventMapper: ActorRef[TypedCheckout.Event] =
    context.messageAdapter {
      case TypedCheckout.PaymentStarted(paymentRef) =>
        ConfirmPaymentStarted(paymentRef)
    }

  val paymentEventMapper: ActorRef[Payment.Event] =
    context.messageAdapter {
      case Payment.ReceivedPayment =>
        ConfirmPaymentReceived
    }

  def start: Behavior[OrderManager.Command] = uninitialized

  def uninitialized: Behavior[OrderManager.Command] = Behaviors.setup { context =>
    context.log.info("Initializing OrderManager ")
    open(context.spawn(new TypedCartActor().start, "CartActor"))
  }

  def open(cartActor: ActorRef[TypedCartActor.Command]): Behavior[OrderManager.Command] = Behaviors.receive {
    (context, message) =>
      message match {
        case AddItem(id, sender) =>
          cartActor ! TypedCartActor.AddItem(id)
          sender ! Done
          Behaviors.same
        case RemoveItem(id, sender) =>
          cartActor ! TypedCartActor.RemoveItem(id)
          sender ! Done
          Behaviors.same
        case Buy(sender) => inCheckout(cartActor, sender)
        case message =>
          context.log.info(s"Received unknown message: $message")
          Behaviors.same
      }
  }

  def inCheckout(
    cartActorRef: ActorRef[TypedCartActor.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] =
    Behaviors.setup { context =>
      cartActorRef ! TypedCartActor.StartCheckout(cartEventMapper)
      Behaviors.receiveMessage {
        case ConfirmCheckoutStarted(checkoutRef) =>
          senderRef ! Done
          inCheckout(checkoutRef)
        case message =>
          context.log.info(s"Received unknown message: $message")
          Behaviors.same
      }
    }

  def inCheckout(checkoutActorRef: ActorRef[TypedCheckout.Command]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case SelectDeliveryAndPaymentMethod(delivery, payment, sender) =>
          checkoutActorRef ! TypedCheckout.SelectDeliveryMethod(delivery)
          checkoutActorRef ! TypedCheckout.SelectPayment(payment, checkoutEventMapper, paymentEventMapper)
          inPayment(sender)
        case message =>
          context.log.info(s"Received unknown message: $message")
          Behaviors.same
      }
    }

  def inPayment(senderRef: ActorRef[Ack]): Behavior[OrderManager.Command] =
    Behaviors.receive { (context, message) =>
      message match {
        case ConfirmPaymentStarted(paymentRef) =>
          senderRef ! Done
          inPayment(paymentRef, senderRef)
        case message =>
          context.log.info(s"Received unknown message: $message")
          Behaviors.same
      }
    }

  def inPayment(
    paymentActorRef: ActorRef[Payment.Command],
    senderRef: ActorRef[Ack]
  ): Behavior[OrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case Pay(sender) =>
        paymentActorRef ! Payment.DoPayment
        inPayment(paymentActorRef, sender)
      case ConfirmPaymentReceived =>
        senderRef ! Done
        finished
      case message =>
        context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }

  def finished: Behavior[OrderManager.Command] = Behaviors.receive { (context, message) =>
    message match {
      case message =>
        context.log.info(s"Received unknown message: $message")
        Behaviors.same
    }
  }
}
