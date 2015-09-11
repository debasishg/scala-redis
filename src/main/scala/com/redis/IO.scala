package com.redis

import java.io._
import java.net.Socket
import org.newsclub.net.unix._

import com.redis.serialization.Parse.parseStringSafe

trait IO extends Log {
  val host: String
  val port: Int
  val timeout: Int

  var socket: Socket = _
  var out: OutputStream = _
  var in: InputStream = _
  var db: Int = _

  def connected = {
    // there's a good chance i am missing something here:
    val isBound = if(socket.isInstanceOf[AFUNIXSocket]) { true } else { socket.isBound() }
    
    socket != null && isBound && !socket.isClosed() && socket.isConnected() && !socket.isInputShutdown() && !socket.isOutputShutdown()
  }

  // Connects the socket, and sets the input and output streams.
  def connect: Boolean = {
    try {
      if(port == -1) {    // unix socket
        val socketFile = new File(host)
        socket = AFUNIXSocket.newInstance()
        socket.connect(new AFUNIXSocketAddress(socketFile))
      }else {
        socket = new Socket(host, port)
      }

      socket.setSoTimeout(timeout)
      socket.setKeepAlive(true)
      socket.setTcpNoDelay(true)

      out = socket.getOutputStream
      in = new BufferedInputStream(socket.getInputStream)
      true
    } catch {
      case x: Throwable =>
        clearFd
        throw new RuntimeException(x)
    }
  }

  // Disconnects the socket.
  def disconnect: Boolean = {
    try {
      socket.close
      out.close
      in.close
      clearFd
      true
    } catch {
      case x: Throwable =>
        false
    }
  }

  def clearFd = {
    socket = null
    out = null
    in = null
  }

  // Wrapper for the socket write operation.
  def write_to_socket(data: Array[Byte])(op: OutputStream => Unit) = op(out)

  // Writes data to a socket using the specified block.
  def write(data: Array[Byte]) = {
    ifDebug("C: " + parseStringSafe(data))
    if (!connected) connect;
    write_to_socket(data){ os =>
      try {
        os.write(data)
        os.flush
      } catch {
        case x: Throwable => throw new RedisConnectionException("connection is closed. write error")
      }
    }
  }

  private val crlf = List(13,10)

  def readLine: Array[Byte] = {
    if(!connected) connect
    var delimiter = crlf
    var found: List[Int] = Nil
    var build = new scala.collection.mutable.ArrayBuilder.ofByte
    while (delimiter != Nil) {
      val next = in.read
      if (next < 0) return null
      if (next == delimiter.head) {
        found ::= delimiter.head
        delimiter = delimiter.tail
      } else {
        if (found != Nil) {
          delimiter = crlf
          build ++= found.reverseMap(_.toByte)
          found = Nil
        }
        build += next.toByte
      }
    }
    build.result
  }

  def readCounted(count: Int): Array[Byte] = {
    if(!connected) connect
    val arr = new Array[Byte](count)
    var cur = 0
    while (cur < count) {
      cur += in.read(arr, cur, count - cur)
    }
    arr
  }
}
