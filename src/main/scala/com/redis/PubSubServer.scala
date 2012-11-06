package com.redis

import akka.actor.Actor

sealed trait Msg
case class Subscribe(channels: Array[String]) extends Msg
case class Register(callback: PubSubMessage => Any) extends Msg
case class Unsubscribe(channels: Array[String]) extends Msg
case object UnsubscribeAll extends Msg
case class Publish(channel: String, msg: String) extends Msg

class Subscriber(client: RedisClient) extends Actor {
  var callback: PubSubMessage => Any = { m => }

  def receive = {
    case Subscribe(channels) =>
      client.subscribe(channels.head, channels.tail: _*)(callback)
      sender ! true

    case Register(cb) =>
      callback = cb
      sender ! true

    case Unsubscribe(channels) =>
      client.unsubscribe(channels.head, channels.tail: _*)
      sender ! true

    case UnsubscribeAll =>
      client.unsubscribe
      sender ! true
  }
}

class Publisher(client: RedisClient) extends Actor {
  def receive = {
    case Publish(channel, message) =>
      client.publish(channel, message)
      sender ! true
  }
}
