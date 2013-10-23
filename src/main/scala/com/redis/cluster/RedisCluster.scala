package com.redis.cluster

import java.util.zip.CRC32
import scala.collection.immutable.TreeSet
import scala.collection.mutable.{ArrayBuffer, ListBuffer}

import com.redis._

import serialization._

/**
 * Consistent hashing distributes keys across multiple servers. But there are situations
 * like <i>sorting</i> or computing <i>set intersections</i> or operations like <tt>rpoplpush</tt>
 * in redis that require all keys to be collocated on the same server.
 * <p/>
 * One of the techniques that redis encourages for such forced key locality is called
 * <i>key tagging</i>. See <http://code.google.com/p/redis/wiki/FAQ> for reference.
 * <p/>
 * The trait <tt>KeyTag</tt> defines a method <tt>tag</tt> that takes a key and returns
 * the part of the key on which we hash to determine the server on which it will be located.
 * If it returns <tt>None</tt> then we hash on the whole key, otherwise we hash only on the
 * returned part.
 * <p/>
 * redis-rb implements a regex based trick to achieve key-tagging. Here is the technique
 * explained in redis FAQ:
 * <i>
 * A key tag is a special pattern inside a key that, if preset, is the only part of the key 
 * hashed in order to select the server for this key. For example in order to hash the key 
 * "foo" I simply perform the CRC32 checksum of the whole string, but if this key has a 
 * pattern in the form of the characters {...} I only hash this substring. So for example 
 * for the key "foo{bared}" the key hashing code will simply perform the CRC32 of "bared". 
 * This way using key tags you can ensure that related keys will be stored on the same Redis
 * instance just using the same key tag for all this keys. Redis-rb already implements key tags.
 * </i>
 */
trait KeyTag {
  def tag(key: Seq[Byte]): Option[Seq[Byte]]
}

import scala.util.matching.Regex
object RegexKeyTag extends KeyTag {

  val tagStart = '{'.toByte
  val tagEnd = '}'.toByte

  def tag(key: Seq[Byte]) = {
    val start = key.indexOf(tagStart) + 1
    if (start > 0) {
      val end = key.indexOf(tagEnd, start)
      if (end > -1) Some(key.slice(start,end)) else None
    } else None
  }
}

object NoOpKeyTag extends KeyTag {
  def tag(key: Seq[Byte]) = Some(key)
}

/**
 * a level of abstraction for each node decoupling it from the address. A node is now identified
 * by a name, so functions like <tt>replaceServer</tt> works seamlessly.
 */
case class ClusterNode(nodename: String, host: String, port: Int, database: Int = 0, maxIdle: Int = 8, secret: Option[Any] = None){
  override def toString = nodename
}

abstract class RedisCluster(hosts: ClusterNode*) extends RedisCommand {

  // not needed at cluster level
  lazy val addr = new FixedAddress(null, 0)

  // abstract val
  val keyTag: Option[KeyTag]

  // default in libmemcached
  val POINTS_PER_SERVER = 160 // default in libmemcached

  // instantiating a cluster will automatically connect participating nodes to the server
  val clients = hosts.toList.map {h => 
    new IdentifiableRedisClientPool(h)
  }

  // the hash ring will instantiate with the nodes up and added
  val hr = HashRing[IdentifiableRedisClientPool](clients, POINTS_PER_SERVER)

  // get node for the key
  def nodeForKey(key: Any)(implicit format: Format): IdentifiableRedisClientPool = {
    val bKey = format(key)
    hr.getNode(keyTag.flatMap(_.tag(bKey)).getOrElse(bKey))
  }
  
  def processForKey[T](key: Any)(body: RedisCommand => T)(implicit format: Format): T = {
    nodeForKey(key).withClient(body(_))
  }

  // add a server
  def addServer(server: ClusterNode) = {
    hr addNode new IdentifiableRedisClientPool(server)
  }

  /**
   * Use Case: Suppose I have a big list of key/value pairs which are replicated in 2 Redis servers -
   * one having test values for every key and the other having production values for the same set of
   * keys. In a cluster using <tt>replaceServer</tt> I can switch between test mode and production mode
   * without disturbing the hash ring. This gives an additional level of abstraction on the node name
   * decoupling it from the node address.
   */

  // replace a server
  // very useful when you want to replace a server in test mode to one in production mode
  def replaceServer(server: ClusterNode) = {
    hr replaceNode new IdentifiableRedisClientPool(server) match {
        case Some(clientPool) => clientPool.close
        case None => 
    }
  }
  
  //remove a server
  def removeServer(nodename: String){
    hr.cluster.find(_.node.nodename.equals(nodename)) match {
      case Some(pool) => {
        hr removeNode(pool)
        pool.close
      }
      case None =>
    }
  }
  
  //list all running servers
  def listServers: List[ClusterNode] = {
    hr.cluster.map(_.node).toList
  }

  /**
   * Operations
   */
  override def keys[A](pattern: Any = "*")(implicit format: Format, parse: Parse[A]) =
    Some(hr.cluster.toList.map(_.withClient(_.keys[A](pattern))).flatten.flatten)

  def onAllConns[T](body: RedisClient => T) = 
    hr.cluster.map(p => p.withClient { client => body(client) }) // .forall(_ == true)

  override def flushdb = onAllConns(_.flushdb) forall(_ == true)
  override def flushall = onAllConns(_.flushall) forall(_ == true)
  override def quit = onAllConns(_.quit) forall(_ == true)
  def close = hr.cluster.map(_.close)

  override def rename(oldkey: Any, newkey: Any)(implicit format: Format): Boolean = processForKey(oldkey)(_.rename(oldkey, newkey))
  override def renamenx(oldkey: Any, newkey: Any)(implicit format: Format): Boolean = processForKey(oldkey)(_.renamenx(oldkey, newkey))
  override def dbsize: Option[Long] =
    Some(onAllConns(_.dbsize).foldLeft(0l)((a, b) => b.map(a+).getOrElse(a)))
  override def exists(key: Any)(implicit format: Format): Boolean = processForKey(key)(_.exists(key))
  override def del(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    Some((key :: keys.toList).groupBy(nodeForKey).foldLeft(0l) { case (t,(n,ks)) => n.withClient{ client => client.del(ks.head,ks.tail:_*).map(t+).getOrElse(t)} })
  override def getType(key: Any)(implicit format: Format) = processForKey(key)(_.getType(key))
  override def expire(key: Any, expiry: Int)(implicit format: Format) = processForKey(key)(_.expire(key, expiry))
  override def expireat(key: Any, expiry: Long)(implicit format: Format) = processForKey(key)(_.expireat(key, expiry))
  override def pexpire(key: Any, expiry: Int)(implicit format: Format) = processForKey(key)(_.pexpire(key, expiry))
  override def pexpireat(key: Any, expiry: Long)(implicit format: Format) = processForKey(key)(_.pexpireat(key, expiry))
  override def select(index: Int) = throw new UnsupportedOperationException("not supported on a cluster")
  override def ttl(key: Any)(implicit format: Format) = processForKey(key)(_.ttl(key))
  override def pttl(key: Any)(implicit format: Format) = processForKey(key)(_.pttl(key))
  override def randomkey[A](implicit parse: Parse[A]) = throw new UnsupportedOperationException("not supported on a cluster")
  override def randkey[A](implicit parse: Parse[A]) = throw new UnsupportedOperationException("not supported on a cluster")


  /**
   * NodeOperations
   */
  override def save = onAllConns(_.save) forall(_ == true)
  override def bgsave = onAllConns(_.bgsave) forall(_ == true)
  override def shutdown = onAllConns(_.shutdown) forall(_ == true)
  override def bgrewriteaof = onAllConns(_.bgrewriteaof) forall(_ == true)

  override def lastsave = throw new UnsupportedOperationException("not supported on a cluster")
  override def monitor = throw new UnsupportedOperationException("not supported on a cluster")
  override def info = throw new UnsupportedOperationException("not supported on a cluster")
  override def slaveof(options: Any) = throw new UnsupportedOperationException("not supported on a cluster")
  override def move(key: Any, db: Int)(implicit format: Format) = throw new UnsupportedOperationException("not supported on a cluster")
  override def auth(secret: Any)(implicit format: Format) = throw new UnsupportedOperationException("not supported on a cluster")


  /**
   * StringOperations
   */
  override def set(key: Any, value: Any)(implicit format: Format) = processForKey(key)(_.set(key, value))
  override def get[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.get(key))
  override def getset[A](key: Any, value: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.getset(key, value))
  override def setnx(key: Any, value: Any)(implicit format: Format) = processForKey(key)(_.setnx(key, value))
  override def setex(key: Any, expiry: Int, value: Any)(implicit format: Format) = processForKey(key)(_.setex(key, expiry, value))
  override def incr(key: Any)(implicit format: Format) = processForKey(key)(_.incr(key))
  override def incrby(key: Any, increment: Int)(implicit format: Format) = processForKey(key)(_.incrby(key, increment))
  override def decr(key: Any)(implicit format: Format) = processForKey(key)(_.decr(key))
  override def decrby(key: Any, increment: Int)(implicit format: Format) = processForKey(key)(_.decrby(key, increment))

  override def mget[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] = {
    val keylist = (key :: keys.toList)
    val kvs = for {
      (n, ks) <- keylist.groupBy(nodeForKey)
      vs <- n.withClient(_.mget[A](ks.head, ks.tail: _*).toList)
      kv <- (ks).zip(vs)
    } yield kv
    Some(keylist.map(kvs))
  }

  override def mset(kvs: (Any, Any)*)(implicit format: Format) = kvs.toList.map{ case (k, v) => set(k, v) }.forall(_ == true)
  override def msetnx(kvs: (Any, Any)*)(implicit format: Format) = kvs.toList.map{ case (k, v) => setnx(k, v) }.forall(_ == true)

  override def setrange(key: Any, offset: Int, value: Any)(implicit format: Format) = processForKey(key)(_.setrange(key, offset, value))
  override def getrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.getrange(key, start, end))
  override def strlen(key: Any)(implicit format: Format) = processForKey(key)(_.strlen(key))
  override def append(key: Any, value: Any)(implicit format: Format) = processForKey(key)(_.append(key, value))
  override def getbit(key: Any, offset: Int)(implicit format: Format) = processForKey(key)(_.getbit(key, offset))
  override def setbit(key: Any, offset: Int, value: Any)(implicit format: Format) = processForKey(key)(_.setbit(key, offset, value)) 
  override def bitop(op: String, destKey: Any, srcKeys: Any*)(implicit format: Format) = throw new UnsupportedOperationException("not supported on a cluster")
  override def bitcount(key: Any, range: Option[(Int, Int)] = None)(implicit format: Format) = processForKey(key)(_.bitcount(key, range))

  /**
   * ListOperations
   */
  override def lpush(key: Any, value: Any, values: Any*)(implicit format: Format) = processForKey(key)(_.lpush(key, value, values:_*))
  override def rpush(key: Any, value: Any, values: Any*)(implicit format: Format) = processForKey(key)(_.rpush(key, value, values:_*))
  override def llen(key: Any)(implicit format: Format) = processForKey(key)(_.llen(key))
  override def lrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.lrange[A](key, start, end))
  override def ltrim(key: Any, start: Int, end: Int)(implicit format: Format) = processForKey(key)(_.ltrim(key, start, end))
  override def lindex[A](key: Any, index: Int)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.lindex(key, index))
  override def lset(key: Any, index: Int, value: Any)(implicit format: Format) = processForKey(key)(_.lset(key, index, value))
  override def lrem(key: Any, count: Int, value: Any)(implicit format: Format) = processForKey(key)(_.lrem(key, count, value))
  override def lpop[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.lpop[A](key))
  override def rpop[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.rpop[A](key))
  override def rpoplpush[A](srcKey: Any, dstKey: Any)(implicit format: Format, parse: Parse[A]) = 
    inSameNode(srcKey, dstKey) {n => n.rpoplpush[A](srcKey, dstKey)}
  override def brpoplpush[A](srcKey: Any, dstKey: Any, timeoutInSeconds: Int)(implicit format: Format, parse: Parse[A]) =
    inSameNode(srcKey, dstKey) {n => n.brpoplpush[A](srcKey, dstKey, timeoutInSeconds)}
  override def blpop[K,V](timeoutInSeconds: Int, key: K, keys: K*)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]) =
    inSameNode((key :: keys.toList): _*) {n => n.blpop[K, V](timeoutInSeconds, key, keys:_*)}
  override def brpop[K,V](timeoutInSeconds: Int, key: K, keys: K*)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]) =
    inSameNode((key :: keys.toList): _*) {n => n.brpop[K, V](timeoutInSeconds, key, keys:_*)}

  private def inSameNode[T](keys: Any*)(body: RedisClient => T)(implicit format: Format): T = {
    val nodes = keys.toList.map(nodeForKey(_))
    nodes.forall(_ == nodes.head) match {
      case true => nodes.head.withClient(body(_))  // all nodes equal
      case _ => 
        throw new UnsupportedOperationException("can only occur if all keys map to same node")
    }
  }

  /**
   * SetOperations
   */
  override def sadd(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] = processForKey(key)(_.sadd(key, value, values:_*))
  override def srem(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] = processForKey(key)(_.srem(key, value, values:_*))
  override def spop[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.spop[A](key))

  override def smove(sourceKey: Any, destKey: Any, value: Any)(implicit format: Format) = 
    inSameNode(sourceKey, destKey) {n => n.smove(sourceKey, destKey, value)}

  override def scard(key: Any)(implicit format: Format) = processForKey(key)(_.scard(key))
  override def sismember(key: Any, value: Any)(implicit format: Format) = processForKey(key)(_.sismember(key, value))

  override def sinter[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sinter[A](key, keys: _*)}

  override def sinterstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sinterstore(key, keys: _*)}

  override def sunion[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sunion[A](key, keys: _*)}

  override def sunionstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sunionstore(key, keys: _*)}

  override def sdiff[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]) = 
    inSameNode((key :: keys.toList): _*) {n => n.sdiff[A](key, keys: _*)}

  override def sdiffstore(key: Any, keys: Any*)(implicit format: Format) = 
    inSameNode((key :: keys.toList): _*) {n => n.sdiffstore(key, keys: _*)}

  override def smembers[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.smembers(key))
  override def srandmember[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.srandmember(key))
  override def srandmember[A](key: Any, count: Int)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.srandmember(key, count))


  import Commands._
  import RedisClient._

  /**
   * SortedSetOperations
   */
  override def zadd(key: Any, score: Double, member: Any, scoreVals: (Double, Any)*)(implicit format: Format) = 
    processForKey(key)(_.zadd(key, score, member, scoreVals:_*))
  override def zrem(key: Any, member: Any, members: Any*)(implicit format: Format): Option[Long] = 
    processForKey(key)(_.zrem(key, member, members: _*))
  override def zincrby(key: Any, incr: Double, member: Any)(implicit format: Format) = processForKey(key)(_.zincrby(key, incr, member))
  override def zcard(key: Any)(implicit format: Format) = processForKey(key)(_.zcard(key))
  override def zscore(key: Any, element: Any)(implicit format: Format) = processForKey(key)(_.zscore(key, element))
  override def zrange[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder )(implicit format: Format, parse: Parse[A]) = 
    processForKey(key)(_.zrange[A](key, start, end, sortAs))
  override def zrangeWithScore[A](key: Any, start: Int = 0, end: Int = -1, sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    processForKey(key)(_.zrangeWithScore[A](key, start, end, sortAs))

  override def zrangebyscore[A](key: Any, min: Double = Double.NegativeInfinity, 
                                minInclusive: Boolean = true, max: Double = Double.PositiveInfinity, 
                                maxInclusive: Boolean = true, limit: Option[(Int, Int)], 
                                sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    processForKey(key)(_.zrangebyscore[A](key, min, minInclusive, max, maxInclusive, limit, sortAs))

  override def zrangebyscoreWithScore[A](key: Any, min: Double = Double.NegativeInfinity, 
                                minInclusive: Boolean = true, max: Double = Double.PositiveInfinity, 
                                maxInclusive: Boolean = true, limit: Option[(Int, Int)], 
                                sortAs: SortOrder = ASC)(implicit format: Format, parse: Parse[A]) =
    processForKey(key)(_.zrangebyscoreWithScore[A](key, min, minInclusive, max, maxInclusive, limit, sortAs))

  override def zcount(key: Any, min: Double = Double.NegativeInfinity, max: Double = Double.PositiveInfinity, 
    minInclusive: Boolean = true, maxInclusive: Boolean = true)(implicit format: Format): Option[Long] =
      processForKey(key)(_.zcount(key, min, max, minInclusive, maxInclusive))

  override def zrank(key: Any, member: Any, reverse: Boolean = false)(implicit format: Format) = processForKey(key)(_.zrank(key, member, reverse))
  override def zremrangebyrank(key: Any, start: Int = 0, end: Int = -1)(implicit format: Format) = processForKey(key)(_.zremrangebyrank(key, start, end))
  override def zremrangebyscore(key: Any, start: Double = Double.NegativeInfinity, 
    end: Double = Double.PositiveInfinity)(implicit format: Format) = processForKey(key)(_.zremrangebyscore(key, start, end))

  override def zunionstore(dstKey: Any, keys: Iterable[Any], 
    aggregate: Aggregate = SUM)(implicit format: Format) = inSameNode((dstKey :: keys.toList):_*) {n => 
      n.zunionstore(dstKey, keys, aggregate)
    }

  override def zunionstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], 
    aggregate: Aggregate = SUM)(implicit format: Format) = inSameNode((dstKey :: kws.map(_._1).toList):_*) {n =>
      n.zunionstoreWeighted(dstKey, kws, aggregate)
    }

  override def zinterstore(dstKey: Any, keys: Iterable[Any], 
    aggregate: Aggregate = SUM)(implicit format: Format) = inSameNode((dstKey :: keys.toList):_*) {n => 
      n.zinterstore(dstKey, keys, aggregate)
    }

  override def zinterstoreWeighted(dstKey: Any, kws: Iterable[Product2[Any,Double]], 
    aggregate: Aggregate = SUM)(implicit format: Format) = inSameNode((dstKey :: kws.map(_._1).toList):_*) {n =>
      n.zinterstoreWeighted(dstKey, kws, aggregate)
    }


  /**
   * HashOperations
   */
  override def hset(key: Any, field: Any, value: Any)(implicit format: Format) = processForKey(key)(_.hset(key, field, value))
  override def hget[A](key: Any, field: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.hget[A](key, field))
  override def hmset(key: Any, map: Iterable[Product2[Any, Any]])(implicit format: Format) = processForKey(key)(_.hmset(key, map))
  override def hmget[K,V](key: Any, fields: K*)(implicit format: Format, parseV: Parse[V]) = processForKey(key)(_.hmget[K,V](key, fields:_*))
  override def hincrby(key: Any, field: Any, value: Int)(implicit format: Format) = processForKey(key)(_.hincrby(key, field, value))
  override def hexists(key: Any, field: Any)(implicit format: Format) = processForKey(key)(_.hexists(key, field))
  override def hdel(key: Any, field: Any, fields: Any*)(implicit format: Format): Option[Long] = processForKey(key)(_.hdel(key, field, fields:_*))
  override def hlen(key: Any)(implicit format: Format) = processForKey(key)(_.hlen(key))
  override def hkeys[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.hkeys[A](key))
  override def hvals[A](key: Any)(implicit format: Format, parse: Parse[A]) = processForKey(key)(_.hvals[A](key))
  override def hgetall[K,V](key: Any)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]) = processForKey(key)(_.hgetall[K,V](key))
}
