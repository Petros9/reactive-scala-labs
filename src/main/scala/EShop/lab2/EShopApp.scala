package EShop.lab2

import akka.actor.{ActorSystem, Props}
import CartActor.{StartCheckout => CartActorStartCheckout, _}
import Checkout.{StartCheckout => CheckoutStartCheckout, _}
import scala.io.StdIn.readLine

class EShopApp extends App {
  val system = ActorSystem("ActorSystem")
  val cartActor = system.actorOf(Props[CartActor], "cartActor")

  while (true) {
    println("Operations: 1 -> add item, 2 -> delete item, 3 - checkout")
    val input = readLine()

   input match {
     case "1" =>
       print("item name: ")
       val item = readLine()
       cartActor ! AddItem(item)

     case "2" =>
       print("item name: ")
       val item = readLine()
       cartActor ! RemoveItem(item)

     case "3" =>
       val checkoutActor = system.actorOf(Props[Checkout], "checkoutActor")
       print("delivery/cancel: ")
       val delivery = readLine()
       if(delivery.equals("cancel")){
         checkoutActor ! CancelCheckout
         cartActor ! ConfirmCheckoutCancelled
       } else {
         checkoutActor ! SelectDeliveryMethod(delivery)
         print("payment/cancel: ")
         val payment = readLine()

         if(payment.equals("cancel")){
           checkoutActor ! CancelCheckout
           cartActor ! ConfirmCheckoutCancelled
         } else {
           checkoutActor ! SelectPayment(payment)
           print("confirm/cancel: ")
           val confirm = readLine()
           if(confirm.equals("cancel")){
             checkoutActor ! CancelCheckout
             cartActor ! ConfirmCheckoutCancelled
           } else {
             checkoutActor ! ConfirmPaymentReceived
             cartActor ! ConfirmCheckoutClosed
           }
         }
       }
     case _ => println("unknown command")
   }
  }
}
