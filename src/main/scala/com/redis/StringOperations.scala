package com.redis

import serialization._

trait StringOperations { self: Redis =>

  // SET KEY (key, value)
  // sets the key with the specified value.
  def set(key: Any, value: Any)(implicit format: Format): Boolean =
    send("SET", List(key, value))(asBoolean)

  // SET key value [EX seconds] [PX milliseconds] [NX|XX]
  // set the string value of a key
  // Starting with Redis 2.6.12 SET supports a set of options that modify its behavior:
  // EX seconds -- Set the specified expire time, in seconds.
  // PX milliseconds -- Set the specified expire time, in milliseconds.
  // NX -- Only set the key if it does not already exist.
  // XX -- Only set the key if it already exist.
  def set(key: Any, value: Any, nxxx: Any, expx: Any, time: Long): Boolean = {
    send("SET", List(key, value, nxxx, expx, time))(asBoolean)
  }

  // GET (key)
  // gets the value for the specified key.
  def get[A](key: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("GET", List(key))(asBulk)

  // GETSET (key, value)
  // is an atomic set this value and return the old value command.
  def getset[A](key: Any, value: Any)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("GETSET", List(key, value))(asBulk)

  // SETNX (key, value)
  // sets the value for the specified key, only if the key is not there.
  def setnx(key: Any, value: Any)(implicit format: Format): Boolean =
    send("SETNX", List(key, value))(asBoolean)

  def setex(key: Any, expiry: Int, value: Any)(implicit format: Format): Boolean =
    send("SETEX", List(key, expiry, value))(asBoolean)

  // INCR (key)
  // increments the specified key by 1
  def incr(key: Any)(implicit format: Format): Option[Long] =
    send("INCR", List(key))(asLong)

  // INCR (key, increment)
  // increments the specified key by increment
  def incrby(key: Any, increment: Int)(implicit format: Format): Option[Long] =
    send("INCRBY", List(key, increment))(asLong)

  // DECR (key)
  // decrements the specified key by 1
  def decr(key: Any)(implicit format: Format): Option[Long] =
    send("DECR", List(key))(asLong)

  // DECR (key, increment)
  // decrements the specified key by increment
  def decrby(key: Any, increment: Int)(implicit format: Format): Option[Long] =
    send("DECRBY", List(key, increment))(asLong)

  // MGET (key, key, key, ...)
  // get the values of all the specified keys.
  def mget[A](key: Any, keys: Any*)(implicit format: Format, parse: Parse[A]): Option[List[Option[A]]] =
    send("MGET", key :: keys.toList)(asList)

  // MSET (key1 value1 key2 value2 ..)
  // set the respective key value pairs. Overwrite value if key exists
  def mset(kvs: (Any, Any)*)(implicit format: Format) =
    send("MSET", kvs.foldRight(List[Any]()){ case ((k,v),l) => k :: v :: l })(asBoolean)

  // MSETNX (key1 value1 key2 value2 ..)
  // set the respective key value pairs. Noop if any key exists
  def msetnx(kvs: (Any, Any)*)(implicit format: Format) =
    send("MSETNX", kvs.foldRight(List[Any]()){ case ((k,v),l) => k :: v :: l })(asBoolean)

  // SETRANGE key offset value
  // Overwrites part of the string stored at key, starting at the specified offset,
  // for the entire length of value.
  def setrange(key: Any, offset: Int, value: Any)(implicit format: Format): Option[Long] =
    send("SETRANGE", List(key, offset, value))(asLong)

  // GETRANGE key start end
  // Returns the substring of the string value stored at key, determined by the offsets
  // start and end (both are inclusive).
  def getrange[A](key: Any, start: Int, end: Int)(implicit format: Format, parse: Parse[A]): Option[A] =
    send("GETRANGE", List(key, start, end))(asBulk)

  // STRLEN key
  // gets the length of the value associated with the key
  def strlen(key: Any)(implicit format: Format): Option[Long] =
    send("STRLEN", List(key))(asLong)

  // APPEND KEY (key, value)
  // appends the key value with the specified value.
  def append(key: Any, value: Any)(implicit format: Format): Option[Long] =
    send("APPEND", List(key, value))(asLong)

  // GETBIT key offset
  // Returns the bit value at offset in the string value stored at key
  def getbit(key: Any, offset: Int)(implicit format: Format): Option[Int] =
    send("GETBIT", List(key, offset))(asInt)

  // SETBIT key offset value
  // Sets or clears the bit at offset in the string value stored at key
  def setbit(key: Any, offset: Int, value: Any)(implicit format: Format): Option[Int] =
    send("SETBIT", List(key, offset, value))(asInt)

  // BITOP op destKey srcKey...
  // Perform a bitwise operation between multiple keys (containing string values) and store the result in the destination key.
  def bitop(op: String, destKey: Any, srcKeys: Any*)(implicit format: Format): Option[Int] =
    send("BITOP", op :: destKey :: srcKeys.toList)(asInt)

  // BITCOUNT key range
  // Count the number of set bits in the given key within the optional range
  def bitcount(key: Any, range: Option[(Int, Int)] = None)(implicit format: Format): Option[Int] =
    send("BITCOUNT", List[Any](key) ++ (range.map { r => List[Any](r._1, r._2) } getOrElse List[Any]()))(asInt)
}
