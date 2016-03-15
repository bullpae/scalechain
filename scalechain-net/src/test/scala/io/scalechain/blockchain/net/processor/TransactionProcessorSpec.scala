package io.scalechain.blockchain.net.processor

import akka.actor.ActorSystem
import akka.actor.Actor
import akka.actor.Props
import akka.testkit.{ TestActors, TestKit, ImplicitSender }
import org.scalatest._

class TransactionProcessorSpec extends TestKit(ActorSystem("")) with ImplicitSender
with WordSpecLike with ShouldMatchers with BeforeAndAfterEach with BeforeAndAfterAll {
  this: Suite =>

  override def beforeEach() {
  // set-up code
  //

  super.beforeEach()
  }

  override def afterEach() {
  super.afterEach()

  // tear-down code
  //
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "actor" should {
    "do something" in {
/*
      val echo = system.actorOf(TestActors.echoActorProps)
      echo ! "hello world"
      expectMsg("hello world")
*/
    }
  }
}