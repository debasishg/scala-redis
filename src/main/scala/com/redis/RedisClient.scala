package com.redis

import serialization.Format
import java.net.SocketException

object RedisClient {
  trait SortOrder
  case object ASC extends SortOrder
  case object DESC extends SortOrder

  trait Aggregate
  case object SUM extends Aggregate
  case object MIN extends Aggregate
  case object MAX extends Aggregate
}

trait Redis extends IO with Protocol {

  def send[A](command: String, args: Seq[Any])(result: => A)(implicit format: Format): A = try {
    write(Commands.multiBulk(command.getBytes("UTF-8") +: (args map (format.apply))))
    result
  } catch {
    case e: RedisConnectionException =>
      if (reconnect) send(command, args)(result)
      else throw e
    case e: SocketException =>
      if (reconnect) send(command, args)(result)
      else throw e
  }

  def send[A](command: String)(result: => A): A = try {
    write(Commands.multiBulk(List(command.getBytes("UTF-8"))))
    result
  } catch {
    case e: RedisConnectionException =>
      if (reconnect) send(command)(result)
      else throw e
    case e: SocketException =>
      if (reconnect) send(command)(result)
      else throw e
  }

  def cmd(args: Seq[Array[Byte]]) = Commands.multiBulk(args)

  protected def flattenPairs(in: Iterable[Product2[Any, Any]]): List[Any] =
    in.iterator.flatMap(x => Iterator(x._1, x._2)).toList
}

trait RedisCommand extends Redis
  with Operations 
  with NodeOperations 
  with StringOperations
  with ListOperations
  with SetOperations
  with SortedSetOperations
  with HashOperations
  with EvalOperations
  

class RedisClient(override val host: String, override val port: Int)
  extends RedisCommand with PubSub {

  connect

  def this() = this("localhost", 6379)
  override def toString = host + ":" + String.valueOf(port)

  def pipeline(f: PipelineClient => Any): Option[List[Any]] = {
    send("MULTI")(asString) // flush reply stream
    try {
      val pipelineClient = new PipelineClient(this)
      f(pipelineClient)
      send("EXEC")(asExec(pipelineClient.handlers))
    } catch {
      case e: RedisMultiExecException => 
        send("DISCARD")(asString)
        None
    }
  }

  class PipelineClient(parent: RedisClient) extends RedisCommand {
    import serialization.Parse

    var handlers: Vector[() => Any] = Vector.empty

    override def send[A](command: String, args: Seq[Any])(result: => A)(implicit format: Format): A = {
      write(Commands.multiBulk(command.getBytes("UTF-8") +: (args map (format.apply))))
      handlers :+= (() => result)
      receive(singleLineReply).map(Parse.parseDefault)
      null.asInstanceOf[A] // ugh... gotta find a better way
    }
    override def send[A](command: String)(result: => A): A = {
      write(Commands.multiBulk(List(command.getBytes("UTF-8"))))
      handlers :+= (() => result)
      receive(singleLineReply).map(Parse.parseDefault)
      null.asInstanceOf[A]
    }

    val host = parent.host
    val port = parent.port

    // TODO: Find a better abstraction
    override def connected = parent.connected
    override def connect = parent.connect
    override def reconnect = parent.reconnect
    override def disconnect = parent.disconnect
    override def clearFd = parent.clearFd
    override def write(data: Array[Byte]) = parent.write(data)
    override def readLine = parent.readLine
    override def readCounted(count: Int) = parent.readCounted(count)
  }
}
