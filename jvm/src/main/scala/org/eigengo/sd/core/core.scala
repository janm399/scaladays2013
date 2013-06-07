package org.eigengo.sd.core

import akka.actor.{Props, ActorSystem}
import com.rabbitmq.client.ConnectionFactory
import com.github.sstone.amqp.ConnectionOwner

case class Begin(minCoins: Int)

/**
 * Contains the functionality of the "headless" part of our app
 */
trait Core {

  // start the actor system
  implicit val system = ActorSystem("recog")

  val amqpConnectionFactory = new ConnectionFactory
  amqpConnectionFactory.setHost("localhost")

  // create a "connection owner" actor, which will try and reconnect automatically if the connection ins lost
  val amqpConnection = system.actorOf(Props(new ConnectionOwner(amqpConnectionFactory)))

  // create the coordinator actor
  val coordinator = system.actorOf(Props(new CoordinatorActor(amqpConnection)), "coordinator")

}
