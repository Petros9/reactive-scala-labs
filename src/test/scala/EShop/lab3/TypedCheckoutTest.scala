package EShop.lab3
import EShop.lab2.{Cart, TypedCartActor}
import EShop.lab2.TypedCartActor.ConfirmCheckoutClosed
import EShop.lab3.OrderManager.ConfirmCheckoutStarted
import akka.actor.testkit.typed.scaladsl.{ActorTestKit, ScalaTestWithActorTestKit}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCheckoutTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  import EShop.lab2.TypedCheckout._

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  it should "Send close confirmation to cart" in {
    val probeCartActor = testKit.createTestProbe[Any]()
    val testTypedCartActor = cartActorWithCartSizeResponseOnStateChange(testKit, probeCartActor.ref)
    val probeOrderManager = testKit.createTestProbe[OrderManager.Command]()
    testTypedCartActor ! TypedCartActor.AddItem("First")
    testTypedCartActor ! TypedCartActor.StartCheckout(probeOrderManager.ref)
    val message = probeOrderManager.receiveMessage()
    val checkoutRef = message match { case ConfirmCheckoutStarted(checkoutRef) => checkoutRef }
    checkoutRef ! SelectDeliveryMethod("credit-card")
    checkoutRef ! SelectPayment("paypal", probeOrderManager.ref)
    checkoutRef ! ConfirmPaymentReceived
    probeCartActor.expectMessage(ConfirmCheckoutClosed)
  }

  def cartActorWithCartSizeResponseOnStateChange(testKit: ActorTestKit,
                                                 probe: ActorRef[Any]
                                                ): ActorRef[TypedCartActor.Command] =
    testKit.spawn {
      val cartActor = new TypedCartActor {
        override def inCheckout(cart: Cart): Behavior[TypedCartActor.Command] = Behaviors.receive(
          (_, message) => {
            probe ! message
            super.inCheckout(cart)
          }
        )
      }
      cartActor.start
    }
}
