package com.redis

import serialization.Parse
import Parse.{Implicits => Parsers}

private [redis] object Commands {

  // Response codes from the Redis server
  val ERR    = '-'
  val OK     = "OK".getBytes("UTF-8")
  val QUEUED = "QUEUED".getBytes("UTF-8")
  val SINGLE = '+'
  val BULK   = '$'
  val MULTI  = '*'
  val INT    = ':'

  val LS     = "\r\n".getBytes("UTF-8")

  def multiBulk(args: Seq[Array[Byte]]): Array[Byte] = {
    val b = new scala.collection.mutable.ArrayBuilder.ofByte
    b ++= "*%d".format(args.size).getBytes
    b ++= LS
    args foreach { arg =>
      b ++= "$%d".format(arg.size).getBytes
      b ++= LS
      b ++= arg
      b ++= LS
    }
    b.result
  }
}

import Commands._

case class RedisConnectionException(message: String) extends RuntimeException(message)
case class RedisMultiExecException(message: String) extends RuntimeException(message)

private [redis] trait Reply {

  type Reply[T] = PartialFunction[(Char, Array[Byte]), T]
  type SingleReply = Reply[Option[Array[Byte]]]
  type MultiReply = Reply[Option[List[Option[Array[Byte]]]]]
  type MultiMultiReply = Reply[Option[List[Option[List[Option[Array[Byte]]]]]]]

  def readLine: Array[Byte]
  def readCounted(c: Int): Array[Byte]
  def reconnect: Boolean

  val integerReply: Reply[Option[Int]] = {
    case (INT, s) => Some(Parsers.parseInt(s))
    case (BULK, s) if Parsers.parseInt(s) == -1 => None
  }

  val longReply: Reply[Option[Long]] = {
    case (INT, s) => Some(Parsers.parseLong(s))
    case (BULK, s) if Parsers.parseInt(s) == -1 => None
  }

  val singleLineReply: SingleReply = {
    case (SINGLE, s) => Some(s)
    case (INT, s) => Some(s)
  }

  val bulkReply: SingleReply = {
    case (BULK, s) => 
      Parsers.parseInt(s) match {
        case -1 => None
        case l => {
          val str = readCounted(l)
          val ignore = readLine // trailing newline
          Some(str)
        }
      }
  }

  val multiBulkReply: MultiReply = {
    case (MULTI, str) =>
      Parsers.parseInt(str) match {
        case -1 => None
        case n => Some(List.fill(n)(receive(bulkReply orElse singleLineReply)))
      }
  }

  val multiMultiBulkReply: MultiMultiReply = {
    case (MULTI, str) =>
      Parsers.parseInt(str) match {
        case -1 => None
        case n => Some(List.fill(n)(receive(multiBulkReply)))
      }
  }

  def execReply(handlers: Seq[() => Any]): PartialFunction[(Char, Array[Byte]), Option[List[Any]]] = {
    case (MULTI, str) =>
      Parsers.parseInt(str) match {
        case -1 => None
        case n if n == handlers.size => 
          Some(handlers.map(_.apply).toList)
        case n => throw new Exception("Protocol error: Expected "+handlers.size+" results, but got "+n)
      }
  }

  val errReply: Reply[Nothing] = {
    case (ERR, s) => reconnect; throw new Exception(Parsers.parseString(s))
    case x => reconnect; throw new Exception("Protocol error: Got " + x + " as initial reply byte")
  }

  def queuedReplyInt: Reply[Option[Int]] = {
    case (SINGLE, QUEUED) => Some(Int.MaxValue)
  }
  
  def queuedReplyLong: Reply[Option[Long]] = {
    case (SINGLE, QUEUED) => Some(Long.MaxValue)
    }

  def queuedReplyList: MultiReply = {
    case (SINGLE, QUEUED) => Some(List(Some(QUEUED)))
  }

  def receive[T](pf: Reply[T]): T = readLine match {
    case null => 
      throw new RedisConnectionException("Connection dropped ..")
    case line =>
      (pf orElse errReply) apply ((line(0).toChar,line.slice(1,line.length)))
  }
}

private [redis] trait R extends Reply {
  def asString: Option[String] = receive(singleLineReply) map Parsers.parseString

  def asBulk[T](implicit parse: Parse[T]): Option[T] =  receive(bulkReply) map parse
  
  def asBulkWithTime[T](implicit parse: Parse[T]): Option[T] = receive(bulkReply orElse multiBulkReply) match {
    case x: Some[Array[Byte]] => x.map(parse(_))
    case _ => None
  }

  def asInt: Option[Int] =  receive(integerReply orElse queuedReplyInt)
  def asLong: Option[Long] =  receive(longReply orElse queuedReplyLong)

  def asBoolean: Boolean = receive(integerReply orElse singleLineReply) match {
    case Some(n: Int) => n > 0
    case Some(s: Array[Byte]) => Parsers.parseString(s) match {
      case "OK" => true
      case "QUEUED" => true
      case _ => false
    }
    case _ => false
  }

  def asList[T](implicit parse: Parse[T]): Option[List[Option[T]]] = receive(multiBulkReply).map(_.map(_.map(parse)))

  def asListPairs[A,B](implicit parseA: Parse[A], parseB: Parse[B]): Option[List[Option[(A,B)]]] =
    receive(multiBulkReply).map(_.grouped(2).flatMap{
      case List(Some(a), Some(b)) => Iterator.single(Some((parseA(a), parseB(b))))
      case _ => Iterator.single(None)
    }.toList)

  def asListOfListPairs[A,B](implicit parseA: Parse[A], parseB: Parse[B]): Option[List[Option[List[Option[(A,B)]]]]] =
    receive(multiMultiBulkReply).map(_.map(_.map(_.grouped(2).map {
      case List(Some(a), Some(b)) => Some((parseA(a), parseB(b)))
      case _ => None
    }.toList)))

  def asQueuedList: Option[List[Option[String]]] = receive(queuedReplyList).map(_.map(_.map(Parsers.parseString)))

  def asExec(handlers: Seq[() => Any]): Option[List[Any]] = receive(execReply(handlers))

  def asSet[T: Parse]: Option[Set[Option[T]]] = asList map (_.toSet)

  def asAny = receive(integerReply orElse singleLineReply orElse bulkReply orElse multiBulkReply)
}

trait Protocol extends R
