package actors


import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import io.circe.parser._

import messages.{DataSent, SendDataToKafka, UnknownMessage}


class KafkaProducerActorTest extends TestKit(ActorSystem("testSystem"))
  with ImplicitSender
  with WordSpecLike
  with BeforeAndAfterAll
  with Matchers {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "An KafkaProducerActor" should {
    val kafkaProducerActor = system.actorOf(Props[KafkaProducerActor], name = "kafkaProducerActor")
    val probe = TestProbe()

    "send back UnknownMessage" in {
      probe.send(kafkaProducerActor, "hi")
      probe.expectMsg(UnknownMessage)
    }

    "send back DataSent" in {
      val data = parse(
        """{\"time\":1571730720,\"states\":[[\"7c35e7\",\"KXL     \",\"Australia\",1571730720," +
        "1571730720,151.023,-31.6746,8534.4,false,194.57,24.19,0,null,8823.96,\"1432\",false,0], " +
        "[\"451d8c\", \"ISS60N  \", \"Bulgaria\", 1571734209, 1571734209, 8.5926, 45.1168, 3314.7, false, 160.9," +
        "335.24, -7.15, null, 3543.3, \"1000\", false, 0]]}""")
      data match {
        case Right(value) =>
          probe.send(kafkaProducerActor, SendDataToKafka(value))
          probe.expectMsgType[DataSent.type]
        case Left(value) => probe.expectNoMessage()
      }
    }
  }
}