package com.redis

import serialization._

trait HyperLogLogOperations { self: Redis =>
  // PFADD (>= 2.8.9)
  // Add a value to the hyperloglog
  def pfadd(key: Any, value: Any, values: Any*): Option[Long] =
    send("PFADD", List(key, value) ::: values.toList)(asLong)

  // PFCOUNT (>= 2.8.9)
  // Get the estimated cardinality from one or more keys
  def pfcount(keys: Any*): Option[Long] =
    send("PFCOUNT", keys.toList)(asLong)

  // PFMERGE (>= 2.8.9)
  // Merge existing keys
  def pfmerge(destination: Any, sources: Any*) =
    send("PFMERGE", List(destination) ::: sources.toList)(asBoolean)
}
