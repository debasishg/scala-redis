package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

// for this Spec to pass enable unix sockets support in redis by setting `unixsocket` in
// redis.conf to /tmp/redis.sock

@RunWith(classOf[JUnitRunner])
class UnixSocketSpec extends FunSpec 
                         with Matchers
                         with BeforeAndAfterEach
                         with BeforeAndAfterAll {

  val r = new RedisClient("/tmp/redis.sock", None)

  override def beforeEach = {
  }

  override def afterEach = {
    r.flushdb
  }

  override def afterAll = {
    r.disconnect
  }

  describe("lpush") {
    it("should add to the head of the list") {
      r.lpush("list-1", "foo") should equal(Some(1))
      r.lpush("list-1", "bar") should equal(Some(2))
    }
    it("should throw if the key has a non-list value") {
      r.set("anshin-1", "debasish") should equal(true)
      val thrown = the [Exception] thrownBy { r.lpush("anshin-1", "bar") }
      thrown.getMessage should include("Operation against a key holding the wrong kind of value")
    }
  }

  describe("sadd") {
    it("should add a non-existent value to the set") {
      r.sadd("set-1", "foo").get should equal(1)
      r.sadd("set-1", "bar").get should equal(1)
    }
    it("should not add an existing value to the set") {
      r.sadd("set-1", "foo").get should equal(1)
      r.sadd("set-1", "foo").get should equal(0)
    }
    it("should fail if the key points to a non-set") {
      r.lpush("list-1", "foo") should equal(Some(1))
      val thrown = the [Exception] thrownBy { r.sadd("list-1", "foo") }
      thrown.getMessage should include ("Operation against a key holding the wrong kind of value")
    }
  }

  describe("set if not exist") {
    it("should set key/value pairs with exclusiveness and expire") {
      r.set("amit-1", "mor", "nx","ex",6)
      r.get("amit-1") match {
        case Some(s: String) => s should equal("mor")
        case None => fail("should return mor")
      }
      r.del("amit-1")
    }
  }

  describe("pipeline1") {
    it("should do pipelined commands") {
      r.pipeline { p =>
        p.set("key", "debasish")
        p.get("key")
        p.get("key1")
      }.get should equal(List(true, Some("debasish"), None))
    }
  }
}
