package actors


import akka.actor.{Actor, ActorSelection}

import io.circe._
import io.circe.parser._

import messages.{CalculateDataMessage, CompleteWork, TransformDataToJSONMessage}


class TransformingActor() extends Actor {
  val calculatingActor: ActorSelection = context.actorSelection("/user/SupervisorActor/calculatingActor")

  override def receive: Receive = {
    case TransformDataToJSONMessage(ingestedData) =>
      val transformedData: Option[Json] = transformDataToJSON(ingestedData)
      transformedData match {
        case Some(value) => calculatingActor ! CalculateDataMessage(value)
        case None => context.parent ! CompleteWork
      }
    case _ => println("Unknown message. Did not start transforming data. TransformingActor.")
  }

  def transformDataToJSON(ingestedData: String): Option[Json] = {
    val jsonData: Either[ParsingFailure, Json] = parse(ingestedData)
    jsonData match {
      case Right(value) => Some(value)
      case Left(value) => None
    }
  }
}
