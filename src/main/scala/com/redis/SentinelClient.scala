package com.redis

class SentinelClient(override val host: String, override val port: Int) extends Redis
  with SentinelOperations
  with PubSub {

  lazy val addr: NodeAddress = new FixedAddress(host, port) // can only be fixed, not a dynamic master

  def this() = this("localhost", 26379)
  override def toString = host + ":" + String.valueOf(port)

  // publishing is not allowed on a sentinel's pub/sub channel
  override def publish(channel: String, msg: String): Option[Long] =
    throw new RuntimeException("Publishing is not supported on a sentinel.")
}
