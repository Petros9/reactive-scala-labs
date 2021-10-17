package EShop.lab2

import akka.actor.{ActorSystem, Props}
import CartActor.{StartCheckout => CartActorStartCheckout, _}
import Checkout.{StartCheckout => CheckoutStartCheckout, _}
import scala.io.StdIn.readLine

object EShopApp  extends App{
  val system = ActorSystem("EShop")
  val cartActor = system.actorOf(Props[CartActor], "mainActor")

  while (true) {
    println("Available operations:\n" +
      "1) add item_name\n" +
      "2) remove item_name\n" +
      "3) Checkout")
    val input = readLine()

    if (input.startsWith("add")) {
      val elems = input.split(" ")
      if (elems.size != 2)
        println("Wrong command")
      else {
        val item = elems(1)
        cartActor ! AddItem(item)
      }
    }
    else if (input.startsWith("remove")) {
      val elems = input.split(" ")
      if (elems.size != 2)
        println("Wrong command")
      else {
        val item = elems(1)
        cartActor ! RemoveItem(item)
      }
    }
    else if (input == "Checkout") {
      cartActor ! CartActorStartCheckout
      val checkoutActor = system.actorOf(Props[Checkout], "checkoutActor")
      checkoutActor ! CheckoutStartCheckout
      println("Enter delivery method/cancel):")
      val deliveryMethod = readLine()

      if (deliveryMethod.toLowerCase() == "cancel") {
        checkoutActor ! CancelCheckout
        cartActor ! ConfirmCheckoutCancelled
      }
      else {
        checkoutActor ! SelectDeliveryMethod(deliveryMethod)
        println("Enter payment method/cancel:")
        val paymentMethod = readLine()

        if (paymentMethod.toLowerCase() == "cancel") {
          checkoutActor ! CancelCheckout
          cartActor ! ConfirmCheckoutCancelled
        }
        else {
          checkoutActor ! SelectPayment(paymentMethod)
          println("(enter \"pay\")/cancel:")
          val payment = readLine()

          if (payment.toLowerCase() == "cancel") {
            checkoutActor ! CancelCheckout
            cartActor ! ConfirmCheckoutCancelled
          }
          else {
            checkoutActor ! ConfirmPaymentReceived
            cartActor ! ConfirmCheckoutClosed
          }
        }
      }
    }
    else
      println(input + " not recognized")
  }
}