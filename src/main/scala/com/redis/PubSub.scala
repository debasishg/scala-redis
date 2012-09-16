package com.redis

object Util {
  object Break extends RuntimeException;
  def break { throw Break }
  def whileTrue(block: => Unit) {
    try {
      while (true)
        try {
          block
        } catch { case Break => return }
    }
  }
}

sealed trait PubSubMessage
case class S(channel: String, noSubscribed: Int) extends PubSubMessage
case class U(channel: String, noSubscribed: Int) extends PubSubMessage
case class M(origChannel: String, message: String) extends PubSubMessage

import Util._
trait PubSub { self: Redis =>
  var pubSub: Boolean = _

  class Consumer(fn: PubSubMessage => Any) extends Runnable {

    def start () {
      val myThread = new Thread(this) ;
      myThread.start() ;
    }

    def run {
      whileTrue {
        asList match {
          case Some(Some(msgType) :: Some(channel) :: Some(data) :: Nil) =>
            msgType match {
              case "subscribe" | "psubscribe" => fn(S(channel, data.toInt))
              case "unsubscribe" if (data.toInt == 0) => 
                fn(U(channel, data.toInt))
                break
              case "punsubscribe" if (data.toInt == 0) => 
                fn(U(channel, data.toInt))
                break
              case "unsubscribe" | "punsubscribe" => 
                fn(U(channel, data.toInt))
              case "message" | "pmessage" => 
                fn(M(channel, data))
              case x => throw new RuntimeException("unhandled message: " + x)
            }
          case _ => break
        }
      }
    }
  }

  def pSubscribe(channel: String, channels: String*)(fn: PubSubMessage => Any) {
    if (pubSub == true) { // already pubsub ing
      pSubscribeRaw(channel, channels: _*)
      return
    }
    pubSub = true
    pSubscribeRaw(channel, channels: _*)
    new Consumer(fn).start
  }

  def pSubscribeRaw(channel: String, channels: String*) {
    send("PSUBSCRIBE", channel :: channels.toList)(())
  }

  def pUnsubscribe = {
    send("PUNSUBSCRIBE")(())
  }

  def pUnsubscribe(channel: String, channels: String*) = {
    send("PUNSUBSCRIBE", channel :: channels.toList)(())
  }
  def subscribe(channel: String, channels: String*)(fn: PubSubMessage => Any) {
    if (pubSub == true) { // already pubsub ing
      subscribeRaw(channel, channels: _*)
    } else {
      pubSub = true
      subscribeRaw(channel, channels: _*)
      new Consumer(fn).start
    }
  }

  def subscribeRaw(channel: String, channels: String*) {
    send("SUBSCRIBE", channel :: channels.toList)(())
  }

  def unsubscribe = {
    send("UNSUBSCRIBE")(())
  }

  def unsubscribe(channel: String, channels: String*) = {
    send("UNSUBSCRIBE", channel :: channels.toList)(())
  }

  def publish(channel: String, msg: String): Option[Long] = {
    send("PUBLISH", List(channel, msg))(asLong)
  }
}
