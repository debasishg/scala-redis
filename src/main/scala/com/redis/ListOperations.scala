package com.redis

import serialization._

trait ListOperations { self: Redis =>

  // LPUSH (Variadic: >= 2.4)
  // add values to the head of the list stored at key
  def lpush(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] =
    send("LPUSH", List(key, value) ::: values.toList)(asLong)

  // RPUSH (Variadic: >= 2.4)
  // add value to the head of the list stored at key
  def rpush(key: Any, value: Any, values: Any*)(implicit format: Format): Option[Long] =
    send("RPUSH", List(key, value) ::: values.toList)(asLong)

  // LLEN
  // return the length of the list stored at the specified key.
  // If the key does not exist zero is returned (the same behaviour as for empty lists). 
  // If the value stored at key is not a list an error is returned.
  def llen(key: Any)(implicit format: Format): Option[Long] =
    send("LLEN", List(key))(asLong)

  // LRANGE
  // return the specified elements of the list stored at the specified key.
  // Start and end are zero-based indexes. 
  def lrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("LRANGE", List(key, start, end))(asList)

  // LTRIM
  // Trim an existing list so that it will contain only the specified range of elements specified.
  def ltrim(key: Any, start: Int, end: Int)(implicit format: Format): Boolean =
    send("LTRIM", List(key, start, end))(asBoolean)

  // LINDEX
  // return the especified element of the list stored at the specified key. 
  // Negative indexes are supported, for example -1 is the last element, -2 the penultimate and so on.
  def lindex[A](key: Any, index: Int)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("LINDEX", List(key, index))(asBulk)

  // LSET
  // set the list element at index with the new value. Out of range indexes will generate an error
  def lset(key: Any, index: Int, value: Any)(implicit format: Format): Boolean =
    send("LSET", List(key, index, value))(asBoolean)

  // LREM
  // Remove the first count occurrences of the value element from the list.
  def lrem(key: Any, count: Int, value: Any)(implicit format: Format): Option[Long] =
    send("LREM", List(key, count, value))(asLong)

  // LPOP
  // atomically return and remove the first (LPOP) or last (RPOP) element of the list
  def lpop[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("LPOP", List(key))(asBulk)

  // RPOP
  // atomically return and remove the first (LPOP) or last (RPOP) element of the list
  def rpop[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("RPOP", List(key))(asBulk)

  // RPOPLPUSH
  // Remove the first count occurrences of the value element from the list.
  def rpoplpush[A](srcKey: Any, dstKey: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("RPOPLPUSH", List(srcKey, dstKey))(asBulk)

  def brpoplpush[A](srcKey: Any, dstKey: Any, timeoutInSeconds: Int)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("BRPOPLPUSH", List(srcKey, dstKey, timeoutInSeconds))(asBulkWithTime)

  def blpop[K,V](timeoutInSeconds: Int, key: K, keys: K*)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]): Option[(K,V)] =
    send("BLPOP", key :: keys.foldRight(List[Any](timeoutInSeconds))(_ :: _))(asListPairs[K,V].flatMap(_.flatten.headOption))

  def brpop[K,V](timeoutInSeconds: Int, key: K, keys: K*)(implicit format: Format, parseK: Parse[K], parseV: Parse[V]): Option[(K,V)] =
    send("BRPOP", key :: keys.foldRight(List[Any](timeoutInSeconds))(_ :: _))(asListPairs[K,V].flatMap(_.flatten.headOption))
}
