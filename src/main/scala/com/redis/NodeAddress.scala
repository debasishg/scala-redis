package com.redis

import java.net.InetSocketAddress

abstract class NodeAddress {
  def addr: (String, Int)

  def onChange(callback: InetSocketAddress => Unit)
  override def toString = {
    val (host, port) = addr
    host + ":" + String.valueOf(port)
  }
}

class FixedAddress(host: String, port: Int) extends NodeAddress {
  val addr = (host, port)
  override def onChange(callback: InetSocketAddress => Unit) { }
}

class SentinelMonitoredMasterAddress(val sentinels: Seq[(String, Int)], val masterName: String) extends NodeAddress
  with Log {

  var master: Option[(String, Int)] = None

  override def addr = master.synchronized {
    master match {
      case Some((h, p)) => (h, p)
      case _ => throw new RuntimeException("All sentinels are down.")
    }
  }

  private var onChangeCallbacks: List[InetSocketAddress => Unit] = Nil

  override def onChange(callback: InetSocketAddress => Unit) = synchronized {
    onChangeCallbacks = callback :: onChangeCallbacks
  }

  private def fireCallbacks(addr: InetSocketAddress) = synchronized {
    onChangeCallbacks foreach (_(addr))
  }

  def stopMonitoring() {
    sentinelListeners foreach (_.stop())
  }

  private val sentinelClients = sentinels.map { case (h, p) =>
    val client = new SentinelClient(h, p)
    master match { // this can be done without synchronization because the threads are not yet live
      case Some(_) =>
      case None =>
        try {
          master = client.getMasterAddrByName(masterName)
        } catch {
          case e: Throwable => error("Error connecting to sentinel.", e)
        }
    }
    client
  }
  private val sentinelListeners = sentinelClients map { client =>
    val listener = new SentinelListener(client)
    new Thread(listener).start()
    listener
  }

  private class SentinelListener(val client: SentinelClient) extends Runnable {
    @volatile var running: Boolean = false

    def run() {
      running = true
      while (running) {
        try {
          client.synchronized {
            client.send("SUBSCRIBE", List("+switch-master"))(())
          }
          new client.Consumer((msg: PubSubMessage) =>
            msg match {
              case M(chan, msgText) =>
                val tokens = msgText split ' '
                val addr = tokens(3)
                val port = tokens(4).toInt
                master.synchronized {
                  master = Some(addr, port)
                }
                fireCallbacks(new InetSocketAddress(addr, port))
              case _ =>
          }).run() // synchronously read, so we know when a disconnect happens
        } catch {
          case e: Throwable => error("Error connecting to sentinel.", e)
        }
      }
    }

    def stop() {
      client.synchronized {
        client.unsubscribe
      }
      running = false
    }
  }
}
