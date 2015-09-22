package com.redis

import akka.actor.{ Actor, ActorSystem, Props }

case class PublishMessage(channel: String, message: String)
case class SubscribeMessage(channels: List[String])
case class UnsubscribeMessage(channels: List[String])
case object GoDown

class Pub extends Actor {
  println("starting publishing service ..")
  val system = ActorSystem("pub")
  val r = new RedisClient("localhost", 6379)
  val p = system.actorOf(Props(new Publisher(r)))

  def receive = {
    case PublishMessage(ch, msg) => publish(ch, msg)
    case GoDown => 
      r.quit
      system.shutdown()
      system.awaitTermination()

    case x => println("Got in Pub " + x)
  }

  def publish(channel: String, message: String) = {
    p ! Publish(channel, message)
  }
}


class Sub extends Actor {
  println("starting subscription service ..")
  val system = ActorSystem("sub")
  val r = new RedisClient("localhost", 6379)
  val s = system.actorOf(Props(new Subscriber(r)))
  s ! Register(callback) 

  def receive = {
    case SubscribeMessage(chs) => sub(chs)
    case UnsubscribeMessage(chs) => unsub(chs)
    case GoDown => 
      r.quit
      system.shutdown()
      system.awaitTermination()

    case x => println("Got in Sub " + x)
  }

  def sub(channels: List[String]) = {
    s ! Subscribe(channels.toArray)
  }

  def unsub(channels: List[String]) = {
    s ! Unsubscribe(channels.toArray)
  }

  def callback(pubsub: PubSubMessage) = pubsub match {
    case E(exception) => println("Fatal error caused consumer dead. Please init new consumer reconnecting to master or connect to backup")
    case S(channel, no) => println("subscribed to " + channel + " and count = " + no)
    case U(channel, no) => println("unsubscribed from " + channel + " and count = " + no)
    case M(channel, msg) => 
      msg match {
        // exit will unsubscribe from all channels and stop subscription service
        case "exit" => 
          println("unsubscribe all ..")
          r.unsubscribe

        // message "+x" will subscribe to channel x
        case x if x startsWith "+" => 
          val s: Seq[Char] = x
          s match {
            case Seq('+', rest @ _*) => r.subscribe(rest.toString){ m => }
          }

        // message "-x" will unsubscribe from channel x
        case x if x startsWith "-" => 
          val s: Seq[Char] = x
          s match {
            case Seq('-', rest @ _*) => r.unsubscribe(rest.toString)
          }

        // other message receive
        case x => 
          println("received message on channel " + channel + " as : " + x)
      }
  }
}

/**
Welcome to Scala version 2.10.2 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_51).
Type in expressions to have them evaluated.
Type :help for more information.

scala> import com.redis._
import com.redis._

scala> import akka.actor.{ Actor, ActorSystem, Props }
import akka.actor.{Actor, ActorSystem, Props}

scala> val ps = ActorSystem("pub")
ps: akka.actor.ActorSystem = akka://pub

scala> val ss = ActorSystem("sub")
ss: akka.actor.ActorSystem = akka://sub

scala> val p = ps.actorOf(Props(new Pub))
p: akka.actor.ActorRef = Actor[akka://pub/user/$a#2075877062]

scala> starting publishing service ..


scala> val s = ss.actorOf(Props(new Sub))
s: akka.actor.ActorRef = Actor[akka://sub/user/$a#724245975]

scala> starting subscription service ..
Got in Sub true


scala> p ! PublishMessage("a", "hello world")

scala> Got in Pub true


scala> s ! SubscribeMessage(List("a"))

scala> Got in Sub true
subscribed to a and count = 1

$ ./redis-cli
redis 127.0.0.1:6379> publish a "hi there"
(integer) 1

scala> p ! PublishMessage("b", "+c")

scala> Got in Pub true


scala> p ! PublishMessage("b", "+d")

scala> Got in Pub true


scala> p ! PublishMessage("b", "-c")

scala> Got in Pub true


scala> p ! PublishMessage("b", "exit")

scala> Got in Pub true
**/
