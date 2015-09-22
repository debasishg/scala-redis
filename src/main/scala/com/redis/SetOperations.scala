package com.redis

import serialization._

trait SetOperations { self: Redis =>

  // SADD (VARIADIC: >= 2.4)
  // Add the specified members to the set value stored at key.
  def sadd(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] =
    send("SADD", List(key, value) ::: values.toList)(asLong)

  // SREM (VARIADIC: >= 2.4)
  // Remove the specified members from the set value stored at key.
  def srem(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] =
    send("SREM", List(key, value) ::: values.toList)(asLong)

  // SPOP
  // Remove and return (pop) a random element from the Set value at key.
  def spop[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("SPOP", List(key))(asBulk)

  // SMOVE
  // Move the specified member from one Set to another atomically.
  def smove(sourceKey: Any, destKey: Any, value: Any)(implicit format: Format): Option[Long] =
    send("SMOVE", List(sourceKey, destKey, value))(asLong)

  // SCARD
  // Return the number of elements (the cardinality) of the Set at key.
  def scard(key: Any)(implicit format: Format): Option[Long] =
    send("SCARD", List(key))(asLong)

  // SISMEMBER
  // Test if the specified value is a member of the Set at key.
  def sismember(key: Any, value: Any)(implicit format: Format): Boolean =
    send("SISMEMBER", List(key, value))(asBoolean)

  // SINTER
  // Return the intersection between the Sets stored at key1, key2, ..., keyN.
  def sinter[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[Set[Option[A]]] =
    send("SINTER", key :: keys.toList)(asSet)

  // SINTERSTORE
  // Compute the intersection between the Sets stored at key1, key2, ..., keyN, 
  // and store the resulting Set at dstkey.
  // SINTERSTORE returns the size of the intersection, unlike what the documentation says
  // refer http://code.google.com/p/redis/issues/detail?id=121
  def sinterstore(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    send("SINTERSTORE", key :: keys.toList)(asLong)

  // SUNION
  // Return the union between the Sets stored at key1, key2, ..., keyN.
  def sunion[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[Set[Option[A]]] =
    send("SUNION", key :: keys.toList)(asSet)

  // SUNIONSTORE
  // Compute the union between the Sets stored at key1, key2, ..., keyN, 
  // and store the resulting Set at dstkey.
  // SUNIONSTORE returns the size of the union, unlike what the documentation says
  // refer http://code.google.com/p/redis/issues/detail?id=121
  def sunionstore(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    send("SUNIONSTORE", key :: keys.toList)(asLong)

  // SDIFF
  // Return the difference between the Set stored at key1 and all the Sets key2, ..., keyN.
  def sdiff[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[Set[Option[A]]] =
    send("SDIFF", key :: keys.toList)(asSet)

  // SDIFFSTORE
  // Compute the difference between the Set key1 and all the Sets key2, ..., keyN, 
  // and store the resulting Set at dstkey.
  def sdiffstore(key: Any, keys: Any*)(implicit format: Format): Option[Long] =
    send("SDIFFSTORE", key :: keys.toList)(asLong)

  // SMEMBERS
  // Return all the members of the Set value at key.
  def smembers[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[Set[Option[A]]] =
    send("SMEMBERS", List(key))(asSet)

  // SRANDMEMBER
  // Return a random element from a Set
  def srandmember[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("SRANDMEMBER", List(key))(asBulk)

  // SRANDMEMBER
  // Return multiple random elements from a Set (since 2.6)
  def srandmember[A](key: Any, count: Int)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("SRANDMEMBER", List(key, count))(asList)

  // SSCAN
  // Incrementally iterate Set elements (since 2.8)
  def sscan[A](key: Any, cursor: Int, pattern: Any = "*", count: Int = 10)(implicit format: Format, parse: Parse[A]): Option[(Option[Int], Option[List[Option[A]]])] =
    send("SSCAN", key :: cursor :: ((x: List[Any]) => if(pattern == "*") x else "match" :: pattern :: x)(if(count == 10) Nil else List("count", count)))(asPair)
}
