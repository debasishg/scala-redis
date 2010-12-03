package com.redis

trait StringOperations { self: Redis =>

  // SET KEY (key, value)
  // sets the key with the specified value.
  def set(key: String, value: String): Boolean = {
    send("SET", key, value)
    asBoolean
  }

  // GET (key)
  // gets the value for the specified key.
  def get(key: String): Option[String] = {
    send("GET", key)
    asString
  }
  
  // GETSET (key, value)
  // is an atomic set this value and return the old value command.
  def getset(key: String, value: String): Option[String] = {
    send("GETSET", key, value)
    asString
  }

  @deprecated("use getset") def getSet(key: String, value: String) = getset(key, value)
  
  // SETNX (key, value)
  // sets the value for the specified key, only if the key is not there.
  def setnx(key: String, value: String): Boolean = {
    send("SETNX", key, value)
    asBoolean
  }

  @deprecated("use setnx") def setUnlessExists(key: String, value: String) = setnx(key, value)

  // SETEX (key, time, value)
  // atomically sets the value for the specified key and expire time (in sec.)
  def setex(key: String, expiry: Int, value: String) : Boolean = {
    send("SETEX", key, String.valueOf(expiry), value)
    asBoolean
  }

  // INCR (key)
  // increments the specified key by 1
  def incr(key: String): Option[Int] = {
    send("INCR", key)
    asInt
  }

  // INCR (key, increment)
  // increments the specified key by increment
  def incrby(key: String, increment: Int): Option[Int] = {
    send("INCRBY", key, String.valueOf(increment))
    asInt
  }

  @deprecated("use incrby") def incrBy(key: String, increment: Int) = incrby(key, increment)

  // DECR (key)
  // decrements the specified key by 1
  def decr(key: String): Option[Int] = {
    send("DECR", key)
    asInt
  }

  // DECR (key, increment)
  // decrements the specified key by increment
  def decrby(key: String, increment: Int): Option[Int] = {
    send("DECRBY", key, String.valueOf(increment))
    asInt
  }

  @deprecated("use decrby") def decrBy(key: String, increment: Int) = decrby(key, increment)

  // MGET (key, key, key, ...)
  // get the values of all the specified keys.
  def mget(key: String, keys: String*) = {
    send("MGET", key, keys: _*)
    asList
  }

  // MSET (key1 value1 key2 value2 ..)
  // set the respective key value pairs. Overwrite value if key exists
  def mset(kvs: (String, String)*) = {
    msetImpl("MSET", kvs: _*)
  }

  // MSETNX (key1 value1 key2 value2 ..)
  // set the respective key value pairs. Noop if any key exists
  def msetnx(kvs: (String, String)*) = {
    msetImpl("MSETNX", kvs: _*)
  }

  @deprecated("use msetnx") def msetUnlessExists(kvs: (String, String)*) = msetnx(kvs: _*)

  private def msetImpl(command: String, kvs: (String, String)*) = {
    var l: List[String] = List()
    kvs.toList.foreach {case (k, v) => l = l ::: List(k, v)}
    send(command, l.head, l.tail: _*)
    asBoolean
  }
}
