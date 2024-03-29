package actors


import akka.actor.{Actor, ActorLogging}
import com.typesafe.config.{Config, ConfigFactory}
import io.tmos.arm.ArmMethods.manage

import java.io.IOException
import java.util
import java.util.Properties

import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import scala.collection.JavaConverters._

import messages.{CompleteWork, GetDataFromKafka, UnknownMessage}


object  KafkaConsumerActor {
  val errorMessage = "An IOException occurs!"

  val config: Config = ConfigFactory.load("OpenSky.conf").getConfig("kafkaconfig")
  val props:Properties = new Properties()
  props.put("bootstrap.servers", config.getString("bootstrap-servers"))
  props.put("key.deserializer", config.getString("key-deserializer"))
  props.put("value.deserializer", config.getString("value-deserializer"))
  props.put("group.id", config.getString("consumer-group"))
}


class KafkaConsumerActor extends Actor with ActorLogging {
  val consumer: KafkaConsumer[String, String] = new KafkaConsumer[String, String](KafkaConsumerActor.props)
  manage(consumer)

  override def receive: Receive = {
    case GetDataFromKafka =>
      val messages: Option[List[String]] = readMessages()
      messages match {
        case Some(value) => sender() ! value
        case None => sender() ! KafkaConsumerActor.errorMessage
      }
    case UnknownMessage => context.parent ! CompleteWork
    case _ =>
      log.info("Unknown message. KafkaConsumer.")
      sender() ! UnknownMessage
  }

  def readMessages(): Option[List[String]] = {
    try {
      val topic: String = KafkaConsumerActor.config.getString("topic")
      consumer.subscribe(util.Arrays.asList(topic))
      val records: Iterable[ConsumerRecord[String, String]] = consumer.poll(KafkaConsumerActor.config.getLong("poll-timeout")).asScala
      Some(records.toList.map(message => message.value().toString))
    }
    catch {
      case error: IOException =>
        log.error(error.getMessage)
        None
    }
  }
}
