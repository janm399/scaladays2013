package org.eigengo.sd.core

import akka.actor._
import scala.concurrent.{ExecutionContext, Future}
import com.rabbitmq.client.AMQP
import com.github.sstone.amqp.{ConnectionOwner, RpcClient}
import akka.util.Timeout
import com.github.sstone.amqp.Amqp.Publish
import com.github.sstone.amqp.RpcClient.Response
import scala.Some
import com.github.sstone.amqp.RpcClient.Request
import com.github.sstone.amqp.Amqp.Delivery
import spray.json.{JsonParser, JsonReader, DefaultJsonProtocol}

private[core] object RecogSessionActor {

  // receive image to be processed
  private[core] case class Image(image: Array[Byte], end: Boolean)
  // receive chunk of a frame to be processed
  private[core] case class Frame(frameChunk: Array[Byte], end: Boolean)

  // information about the running session
  private[core] case object GetInfo

  // FSM states
  private[core] sealed trait State
  private[core] case object Idle extends State
  private[core] case object Completed extends State
  private[core] case object Aborted extends State
  private[core] case object Active extends State

  // FSM data
  private[core] sealed trait Data
  private[core] case object Empty extends Data
  private[core] case class Running(minCoins: Int, decoder: Option[DecoderContext]) extends Data

  // CV responses
  private[core] case class Coin(center: Double, radius: Double)
  private[core] case class CoinResponse(coins: List[Coin], succeeded: Boolean)

}

/**
 * This actor deals with the states of the recognition session. We use FSM here--even though
 * we only have a handful of states, recognising things sometimes needs many more states
 * and using ``become`` and ``unbecome`` of the ``akka.actor.ActorDSL._`` would be cumbersome.
 *
 * @param amqpConnection the AMQP connection we will use to create individual 'channels'
 * @param jabberActor the actor that will receive our output
 */
private[core] class RecogSessionActor(amqpConnection: ActorRef, jabberActor: ActorRef) extends Actor with
  FSM[RecogSessionActor.State, RecogSessionActor.Data] {

  import RecogSessionActor._
  import scala.concurrent.duration._
  import context.dispatcher

  // default timeout for all states
  val stateTimeout = 60.seconds

  // thank goodness for British spelling :)
  val emptyBehaviour: StateFunction = { case _ => stay() }

  startWith(Idle, Empty)
  // when we receive the ``Begin`` even when idle, we become ``Active``
  when(Idle, stateTimeout) {
    case Event(Begin(minCoins), _) =>
      sender ! self.path.name
      goto(Active) using Running(minCoins, None)
  }

  // when ``Active``, we can process images and frames
  when(Active, stateTimeout) {
    case Event(_, _) =>
      goto(Completed)
  }

  // until we hit Aborted and Completed, which do nothing interesting
  when(Aborted)(emptyBehaviour)
  when(Completed)(emptyBehaviour)

  // unhandled events in the states
  whenUnhandled {
    case Event(StateTimeout, _) => goto(Aborted)
    case Event(GetInfo, _)      => sender ! "OK"; stay()
  }

  // go!
  initialize

}

