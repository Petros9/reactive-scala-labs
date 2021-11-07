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
    testTypedCartActor.run(TypedCartActor.AddItem("First Item"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithFirstItem = Cart.empty.addItem("First Item")
    typedCartInbox.expectMessage(cartWithFirstItem)
    testTypedCartActor.run(TypedCartActor.AddItem("Second Item"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithSecondItem = cartWithFirstItem.addItem("Second Item")
    typedCartInbox.expectMessage(cartWithSecondItem)
  }

  it should "add item properly (asynchronous)" in {
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cartActor")
    val probe = testKit.createTestProbe[Cart]()
    testTypedCartActor ! AddItem("First Item")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithFirstItem = Cart.empty.addItem("First Item")
    probe.expectMessage(cartWithFirstItem)
    testTypedCartActor ! AddItem("Second Item")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithSecondItem = cartWithFirstItem.addItem("Second Item")
    probe.expectMessage(cartWithSecondItem)
    testKit.stop(testTypedCartActor)
  }

  it should "be empty after adding and removing the same item" in {
    val testTypedCartActor = BehaviorTestKit(new TypedCartActor().start)
    val typedCartInbox = TestInbox[Cart]()
    testTypedCartActor.run(TypedCartActor.AddItem("First Item"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    val cartWithFirstItem = Cart.empty.addItem("First Item")
    typedCartInbox.expectMessage(cartWithFirstItem)
    testTypedCartActor.run(TypedCartActor.RemoveItem("First Item"))
    testTypedCartActor.run(TypedCartActor.GetItems(typedCartInbox.ref))
    typedCartInbox.expectMessage(Cart.empty)
  }

  it should "be empty after adding and removing the same item (asynchronous)" in {
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cartActor")
    val probe = testKit.createTestProbe[Cart]()
    testTypedCartActor ! AddItem("First Item")
    testTypedCartActor ! GetItems(probe.ref)
    val cartWithFirstItem = Cart.empty.addItem("First Item")
    probe.expectMessage(cartWithFirstItem)
    testTypedCartActor ! RemoveItem("First Item")
    testTypedCartActor ! GetItems(probe.ref)
    probe.expectMessage(Cart.empty)
    testKit.stop(testTypedCartActor)
  }


  it should "start checkout (asynchronous)" in {
    val managerCartMapperInbox = TestInbox[TypedCartActor.Event]()
    val testTypedCartActor = testKit.spawn(new TypedCartActor().start, "cartActor")
    val probeTypedCartActor = testKit.createTestProbe[Cart]()
    val probeOrderManager = testKit.createTestProbe[OrderManager.Command]()
    // Add item:
    testTypedCartActor ! AddItem("First Item")
    testTypedCartActor ! GetItems(probeTypedCartActor.ref)
    val cartWithFirstItem = Cart.empty.addItem("First Item")
    probeTypedCartActor.expectMessage(cartWithFirstItem)
    // Start checkout:
    testTypedCartActor ! TypedCartActor.StartCheckout(managerCartMapperInbox.ref)
    testKit.stop(testTypedCartActor)
  }
}