package com.redis.ds

trait Deque[A] {
  // inserts at the head
  def addFirst(a: A): Option[Long]

  // inserts at the tail 
  def addLast(a: A): Option[Long]

  // clears the deque
  def clear: Boolean

  // retrieves, but does not remove the head
  def peekFirst: Option[A]

  // retrieves, but does not remove the tail
  def peekLast: Option[A]

  // true, if empty
  def isEmpty: Boolean

  // retrieves and removes the head element of the deque
  def poll: Option[A]

  // retrieves and removes the head element of the deque
  def pollFirst: Option[A]

  // retrieves and removes the tail element of the deque
  def pollLast: Option[A]

  // size of the deque
  def size: Long
}

import com.redis.ListOperations
import com.redis.serialization._
import Parse.Implicits._

abstract class RedisDeque[A](val blocking: Boolean = false, val timeoutInSecs: Int = 0)(implicit private val format: Format, private val parse: Parse[A])
  extends Deque[A] { self: ListOperations =>

  val key: String

  def addFirst(a: A): Option[Long] = lpush(key, a) 
  def addLast(a: A): Option[Long] = rpush(key, a)

  def peekFirst: Option[A] = lrange[A](key, 0, 0).map(_.head.get) 

  def peekLast: Option[A] = lrange[A](key, -1, -1).map(_.head.get) 

  def poll =
    if (blocking == true) {
      blpop[String, A](timeoutInSecs, key).map(_._2)
    } else lpop[A](key)

  def pollFirst: Option[A] = poll

  def pollLast: Option[A] =
    if (blocking == true) {
      brpop[String, A](timeoutInSecs, key).map(_._2)
    } else rpop[A](key) 

  def size: Long = llen(key) getOrElse(0l)

  def isEmpty: Boolean = size == 0

  def clear: Boolean = size match {
    case 0 => true
    case 1 => 
      val n = poll
      true
    case x => ltrim(key, -1, 0)
  }
}

import com.redis.{Redis, RedisCommand}

class RedisDequeClient(val h: String, val p: Int, val d: Int = 0, val s: Option[Any] = None) {
  def getDeque[A](k: String, blocking: Boolean = false, timeoutInSecs: Int = 0)(implicit format: Format, parse: Parse[A]) =
    new RedisDeque(blocking, timeoutInSecs)(format, parse) with RedisCommand {
      val host = h
      val port = p
      val key = k
      override val database = d
      override val secret = s
      initialize
    }
}
