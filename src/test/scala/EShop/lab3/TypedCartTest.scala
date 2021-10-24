package EShop.lab3

import EShop.lab2.{Cart, TypedCartActor, TypedCheckout}
import akka.actor.testkit.typed.Effect._
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpecLike
import org.scalatest.matchers.should.Matchers

class TypedCartTest
  extends ScalaTestWithActorTestKit
    with AnyFlatSpecLike
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  override def afterAll: Unit =
    testKit.shutdownTestKit()

  import TypedCartActor._

  //use GetItems command which was added to make test easier
  it should "add item properly" in {
    val testTypedCartActor = BehaviorTestKit(new TypedCartActor().start)
    val typedCartInbox = TestInbox[Cart]()
    testTypedCartActor.run(TypedCartActor.AddItem("First"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithFirstItem = Cart.empty.addItem("First")
    typedCartInbox.expectMessage(cartWithFirstItem)
    testTypedCartActor.run(TypedCartActor.AddItem("Second"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithSecondItem = cartWithFirstItem.addItem("Second")
    typedCartInbox.expectMessage(cartWithSecondItem)
  }

  it should "add item properly (asynchronous)" in {
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cart")
    val probe = testKit.createTestProbe[Cart]()
    testTypedCartActor ! AddItem("First")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithFirstItem = Cart.empty.addItem("First")
    probe.expectMessage(cartWithFirstItem)
    testTypedCartActor ! AddItem("Second")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithSecondItem = cartWithFirstItem.addItem("Second")
    probe.expectMessage(cartWithSecondItem)
    testKit.stop(testTypedCartActor)
  }

  it should "be empty after adding and removing the same item" in {
    val testTypedCartActor = BehaviorTestKit(new TypedCartActor().start)
    val typedCartInbox = TestInbox[Cart]()
    testTypedCartActor.run(TypedCartActor.AddItem("First"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithFirstItem = Cart.empty.addItem("First")
    typedCartInbox.expectMessage(cartWithFirstItem)
    testTypedCartActor.run(TypedCartActor.RemoveItem("First"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    typedCartInbox.expectMessage(Cart.empty)
  }

  it should "be empty after adding and removing the same item (asynchronous)" in {
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cart")
    val probe = testKit.createTestProbe[Cart]()
    testTypedCartActor ! AddItem("First")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithFirstItem = Cart.empty.addItem("First")
    probe.expectMessage(cartWithFirstItem)
    testTypedCartActor ! RemoveItem("First")
    testTypedCartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
    testKit.stop(testTypedCartActor)
  }

  it should "start checkout" in {
    val testTypedCartActor = BehaviorTestKit(new TypedCartActor().start)
    val typedCartInbox = TestInbox[Cart]()
    val testOrderManagerInbox = TestInbox[OrderManager.Command]()
    // Add item:
    testTypedCartActor.run(TypedCartActor.AddItem("First"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithFirstItem = Cart.empty.addItem("First")
    typedCartInbox.expectMessage(cartWithFirstItem)
    // Start checkout:
    testTypedCartActor.run(TypedCartActor.StartCheckout(testOrderManagerInbox.ref))
    // First efect = Scheduled timer
    testTypedCartActor.expectEffectType[Scheduled[String]]
    testTypedCartActor.expectEffectType[Spawned[String]]
    testOrderManagerInbox.hasMessages
  }

  it should "start checkout (asynchronous)" in {
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cart")
    val probeTypedCartActor = testKit.createTestProbe[Cart]()
    val probeOrderManager = testKit.createTestProbe[OrderManager.Command]()
    // Add item:
    testTypedCartActor ! AddItem("First")
    testTypedCartActor ! GetItems(probeTypedCartActor.ref)
    val cartWithFirstItem = Cart.empty.addItem("First")
    probeTypedCartActor.expectMessage(cartWithFirstItem)
    // Start checkout:
    testTypedCartActor ! TypedCartActor.StartCheckout(probeOrderManager.ref)
    testKit.stop(testTypedCartActor)
  }
}