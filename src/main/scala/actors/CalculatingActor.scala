package actors


import akka.actor.{Actor, ActorLogging, ActorSelection}
import com.typesafe.config.{Config, ConfigFactory}
import collection.JavaConverters._

import io.circe.{HCursor, Json}
import io.circe.syntax._

import scala.util.Try
import scala.util.control.NonFatal

import messages.{CalculateData, CompleteWork, DataCalculated, DataSent, SendDataToKafka, UnknownMessage}


object CalculatingActor {
  val defaultValue: Int = 0 //default value for gerOrElse statements
  val altitudeIndex: Int = 7
  val speedIndex: Int = 9
  val airplaneLongtitudeIndex: Int = 5
  val airplaneLattitudeIndex: Int = 6
}


class CalculatingActor() extends Actor with ActorLogging {
  val kafkaProducerActor: ActorSelection = context.actorSelection("/user/SupervisorActor/kafkaProducerActor")

  val config: Config = ConfigFactory.load("OpenSky.conf")

  override def receive: Receive = {
    case CalculateData(data) =>
      val extractedData: Option[(Json, List[Json])] = parseJSONData(data)
      val highestAltitude: Option[Double] = findHighestAltitude(extractedData)
      val highestSpeed: Option[Double] = findHighestSpeed(extractedData)
      val countOfAirplanes: Option[Int]  = findCountOfAirplanes(extractedData)

      val results: Json = convertResultsToJson(highestAltitude, highestSpeed, countOfAirplanes)
      kafkaProducerActor ! SendDataToKafka(results)
      sender() ! DataCalculated(results)
    case DataSent => log.info("Data was sent to Kafka.")
    case UnknownMessage => context.parent ! CompleteWork
    case _ =>
      log.info("Unknown message. Did not start calculating data. CalculatingActor.")
      sender() ! UnknownMessage
  }

  def parseJSONData(data: Json): Option[(Json, List[Json])] = {
    try {
      val timestamp: Json = data.findAllByKey("time").head
      val cursor: HCursor = data.hcursor
      val states: Option[List[Json]] = cursor.downField("states").values.map(_.toList)
      states match {
        case Some(value) => Some(timestamp, value)
        case _ => None
      }
    }
    catch {
      case NonFatal(error) =>
        log.error(error.getMessage)
        None
    }
  }

  def parseStateList(item: Json): List[String] = {
    val cursor: HCursor = item.hcursor
    val listWithStringValues: List[String] = cursor.values.get.toList.map(_.toString)
    listWithStringValues
  }

  def findHighestAltitude(data: Option[(Json, List[Json])]): Option[Double] = {
    try {
      data match {
        case Some(value) =>
          val states: List[Json] = value._2
          val listOfAltitudes: List[String] = states.map({ item =>
            parseStateList(item)(CalculatingActor.altitudeIndex)
          })
          val maxAltitude: Double = listOfAltitudes.flatMap(item => Try(item.toDouble).toOption).max
          Some(maxAltitude)
        case _ => None
      }
    }
    catch {
      case NonFatal(error) =>
        log.error(error.getMessage)
        None
    }
  }

  def findHighestSpeed(data: Option[(Json, List[Json])]): Option[Double] = {
    try {
      data match {
        case Some(value) =>
          val states: List[Json] = value._2
          val listOfSpeed: List[String] = states.map({ item =>
            parseStateList(item)(CalculatingActor.speedIndex)
          })
          val maxSpeed: Double = listOfSpeed.flatMap(item => Try(item.toDouble).toOption).max
          Some(maxSpeed)
        case _ => None
      }
    }
    catch {
      case NonFatal(error) =>
        log.error(error.getMessage)
        None
    }
  }

  def findCountOfAirplanes(data: Option[(Json, List[Json])]): Option[Int] = {
    val airports = config.getConfigList("airportsconfig.airports").asScala.toList

    val listOfPlanesByAirport = airports.map({ airport =>
      val airportLatitude: Double = airport.getString("lat").toDouble
      val airportLongtitude: Double = airport.getString("long").toDouble
      val radius: Double = config.getDouble("airportsconfig.radius")
      data match {
        case Some(value) =>
          val planeStates = value._2.map(parseStateList).count(plane =>
            plane(CalculatingActor.airplaneLongtitudeIndex) != "null" &&
              plane(CalculatingActor.airplaneLattitudeIndex) != "null" &&
              plane(CalculatingActor.airplaneLattitudeIndex).toDouble >= airportLatitude - radius &&
              plane(CalculatingActor.airplaneLattitudeIndex).toDouble <= airportLatitude + radius &&
              plane(CalculatingActor.airplaneLongtitudeIndex).toDouble >= airportLongtitude - radius &&
              plane(CalculatingActor.airplaneLongtitudeIndex).toDouble <= airportLongtitude + radius)
          Some(planeStates)
        case _ => None
      }
    })

    val countOfAllAirplanes: Int = listOfPlanesByAirport.map(_.getOrElse(CalculatingActor.defaultValue)).sum
    Some(countOfAllAirplanes)
  }

  def convertResultsToJson(highestAttitude: Option[Double], highestSpeed: Option[Double], countOfAirplanes: Option[Int]): Json = {
    Map("highestAttitude" -> highestAttitude.getOrElse(CalculatingActor.defaultValue.toDouble),
      "highestSpeed" -> highestSpeed.getOrElse(CalculatingActor.defaultValue.toDouble),
      "countOfAirplanes" -> countOfAirplanes.getOrElse(CalculatingActor.defaultValue).toDouble
    ).asJson
  }
}
