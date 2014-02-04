package com.redis

import serialization._

trait StringOperations { self: Redis =>

  // SET KEY (key, value)
  // sets the key with the specified value.
  def set(key: Any, value: Any)(implicit format: Format): Boolean =
    send("SET", List(key, value))(asBoolean)

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

  // SETEX (key, time, value)
  // atomically sets the value for the specified key and expire time (in sec.)
  def setex(key: String, expiry: Int, value: String) : Boolean = {
    send("SETEX", key, String.valueOf(expiry), value)
    asBoolean
  }

  // INCR (key)
  // increments the specified key by 1
  def incr(key: Any)(implicit format: Format): Option[Int] =
    send("INCR", List(key))(asInt)

  // INCR (key, increment)
  // increments the specified key by increment
  def incrby(key: Any, increment: Int)(implicit format: Format): Option[Int] =
    send("INCRBY", List(key, increment))(asInt)

  // DECR (key)
  // decrements the specified key by 1
  def decr(key: Any)(implicit format: Format): Option[Int] =
    send("DECR", List(key))(asInt)

  // DECR (key, increment)
  // decrements the specified key by increment
  def decrby(key: Any, increment: Int)(implicit format: Format): Option[Int] =
    send("DECRBY", List(key, increment))(asInt)

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
}
