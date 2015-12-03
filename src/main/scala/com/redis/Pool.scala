package com.redis

import org.apache.commons.pool._
import org.apache.commons.pool.impl._
import com.redis.cluster.ClusterNode

private [redis] class RedisClientFactory(val host: String, val port: Int, val database: Int = 0, val secret: Option[Any] = None, val timeout : Int = 0)
  extends PoolableObjectFactory[RedisClient] {

  // when we make an object it's already connected
  def makeObject = {
    new RedisClient(host, port, database, secret, timeout)
  }

  // quit & disconnect
  def destroyObject(rc: RedisClient): Unit = {
    rc.quit // need to quit for closing the connection
    rc.disconnect // need to disconnect for releasing sockets
  }

  // noop: we want to have it connected
  def passivateObject(rc: RedisClient): Unit = {}
  def validateObject(rc: RedisClient) = rc.connected == true

  // noop: it should be connected already
  def activateObject(rc: RedisClient): Unit = {}
}

object RedisClientPool {
  val UNLIMITED_CONNECTIONS = -1

  val WHEN_EXHAUSTED_BLOCK = GenericObjectPool.WHEN_EXHAUSTED_BLOCK
  val WHEN_EXHAUSTED_FAIL = GenericObjectPool.WHEN_EXHAUSTED_FAIL
  val WHEN_EXHAUSTED_GROW = GenericObjectPool.WHEN_EXHAUSTED_GROW
}

class RedisClientPool(val host: String, val port: Int, val maxIdle: Int = 8, val database: Int = 0, val secret: Option[Any] = None, val timeout : Int = 0, 
    val maxConnections: Int = RedisClientPool.UNLIMITED_CONNECTIONS, val whenExhaustedBehavior: Byte = RedisClientPool.WHEN_EXHAUSTED_BLOCK, val poolWaitTimeout: Long = 3000) {
  val pool = new GenericObjectPool(new RedisClientFactory(host, port, database, secret, timeout), maxConnections, whenExhaustedBehavior, poolWaitTimeout, maxIdle, false, true)
  override def toString = host + ":" + String.valueOf(port)

  def withClient[T](body: RedisClient => T) = {
    val client = pool.borrowObject
    try {
      body(client)
    } finally {
      pool.returnObject(client)
    }
  }

  // close pool & free resources
  def close = pool.close
}

/**
 *
 * @param poolname must be unique
 */
class IdentifiableRedisClientPool(val node: ClusterNode)
  extends RedisClientPool (node.host, node.port, node.maxIdle, node.database, node.secret,node.timeout){
  override def toString = node.nodename
}
