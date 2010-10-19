package com.redis

private [redis] object Commands {

  // Response codes from the Redis server
  val ERR    = '-'
  val OK     = "OK"
  val QUEUED = "QUEUED"
  val SINGLE = '+'
  val BULK   = '$'
  val MULTI  = '*'
  val INT    = ':'

  val LS     = "\r\n"
}

import Commands._

private [redis] sealed abstract class Command(command: String)

private [redis] trait WithLS {
  def withLS(str: String, strs: String*) = 
    (str :: strs.toList) mkString(LS)
}

private [redis] case class MultiBulkCommand(command: String, key: String, values: String*) 
  extends Command(command) with WithLS { 
  override def toString: String = {
    val nelems = "%s%d".format(MULTI, values.toList.size + 2)
    val cl = "%s%d".format(BULK, command.length)
    val kl = "%s%d".format(BULK, key.length)
    val vs = values.toList.map(v => "%s%d%s%s%s".format(BULK, v.length, LS, v, LS))
    withLS(nelems, cl, command, kl, key, vs.mkString)
  }
}

private [redis] case class InlineCommand(command: String) extends Command(command) {
  override def toString: String = 
    "%s%s".format(command, LS)
}

private [redis] trait C {
  def snd(command: String, key: String, values: String*)(to: String => Unit) =
    to(MultiBulkCommand(command, key, values:_*).toString)

  def snd(command: String)(to: String => Unit) = 
    to(InlineCommand(command).toString)
}

case class RedisConnectionException(message: String) extends RuntimeException(message)
case class RedisMultiExecException(message: String) extends RuntimeException(message)

private [redis] trait Reply {

  def readLine: String
  def readCounted(c: Int): String
  def reconnect: Boolean

  val integerReply: PartialFunction[(Char, String), Option[Int]] = {
    case (INT, s) => Some(Integer.parseInt(s))
  }

  val singleLineReply: PartialFunction[(Char, String), Option[String]] = {
    case (SINGLE, s) => Some(s)
    case (INT, s) => Some(s)
  }

  val bulkReply: PartialFunction[(Char, String), Option[String]] = {
    case (BULK, s) => 
      Integer.parseInt(s) match {
        case -1 => None
        case l => {
          val str = readCounted(l)
          val ignore = readLine // trailing newline
          Some(str)
        }
      }
  }

  val multiBulkReply: PartialFunction[(Char, String), Option[List[Option[String]]]] = {
    case (MULTI, str) =>
      Integer.parseInt(str) match {
        case -1 => None
        case n => 
          var l = List[Option[String]]()
          (1 to n).foreach {i =>
            l = l ::: List(receive(bulkReply orElse singleLineReply))
          }
          Some(l)
      }
  }

  val execReply: PartialFunction[(Char, String), Option[List[Option[Any]]]] = {
    case (MULTI, str) =>
      Integer.parseInt(str) match {
        case -1 => None
        case n => 
          var l = List[Option[Any]]()
          (1 to n).foreach {i =>
            l = l ::: List(receive(integerReply orElse singleLineReply orElse bulkReply orElse multiBulkReply))
          }
          Some(l)
      }
  }

  val errReply: PartialFunction[(Char, String), Nothing] = {
    case (ERR, s) => reconnect; throw new Exception(s)
    case x => reconnect; throw new Exception("Protocol error: Got " + x + " as initial reply byte")
  }

  def queuedReplyInt: PartialFunction[(Char, String), Option[Int]] = {
    case (SINGLE, QUEUED) => Some(Int.MaxValue)
  }

  def queuedReplyList: PartialFunction[(Char, String), Option[List[Option[String]]]] = {
    case (SINGLE, QUEUED) => Some(List(Some(QUEUED)))
  }

  def receive[T](pf: PartialFunction[(Char, String), T]): T = readLine match {
    case null =>
      throw new RedisConnectionException("Connection dropped ..")
    case line =>
      (pf orElse errReply) apply ((line(0), line.substring(1, line.length)))
  }
}

private [redis] trait R extends Reply {
  def asString = receive(singleLineReply orElse bulkReply)
  
  def asInt = receive(integerReply orElse queuedReplyInt)

  def asBoolean = receive(integerReply orElse singleLineReply) match {
    case Some(n: Int) => n > 0
    case Some(s: String) => s == OK || s == QUEUED
    case _ => false
  }

  def asList = receive(multiBulkReply orElse queuedReplyList)
  def asExec = receive(execReply)

  def asSet = asList map (l => Set(l: _*))

  def asAny = receive(integerReply orElse singleLineReply orElse bulkReply orElse multiBulkReply)
}

trait Protocol extends C with R
