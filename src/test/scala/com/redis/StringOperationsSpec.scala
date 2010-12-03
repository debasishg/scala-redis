package com.redis

import org.scalatest.Spec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class StringOperationsSpec extends Spec 
                           with ShouldMatchers
                           with BeforeAndAfterEach
                           with BeforeAndAfterAll {

  val r = new RedisClient("localhost", 6379)

  override def beforeEach = {
  }

  override def afterEach = {
    r.flushdb
  }

  override def afterAll = {
    r.disconnect
  }

  describe("set") {
    it("should set key/value pairs") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
    }
  }

  describe("get") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
    }
    it("should fail for non-existent keys") {
      r.get("anshin-2") match {
        case Some(s: String) => fail("should return None")
        case None => 
      }
    }
  }

  describe("getset") {
    it("should set new values and return old values") {
      r.set("anshin-1", "debasish") should equal(true)
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
      r.getset("anshin-1", "maulindu") match {
        case Some(s: String) => s should equal("debasish")
        case None => fail("should return debasish")
      }
      r.get("anshin-1") match {
        case Some(s: String) => s should equal("maulindu")
        case None => fail("should return maulindu")
      }
    }
  }

  describe("setnx") {
    it("should set only if the key does not exist") {
      r.set("anshin-1", "debasish") should equal(true)
      r.setnx("anshin-1", "maulindu") should equal(false)
      r.setnx("anshin-2", "maulindu") should equal(true)
    }
  }

  describe("setex") {
    it("should set values with expiry") {
      val key = "setex-1"
      val value = "value"
      r.setex(key, 1, value) should equal(true)
      r.get(key) match {
        case Some(s:String) => s should equal(value)
        case None => fail("should return value")
      }
      Thread.sleep(2000)
      r.get(key) match {
        case Some(_) => fail("key-1 should have expired")
        case None =>
      }
    }
  }

  describe("incr") {
    it("should increment by 1 for a key that contains a number") {
      r.set("anshin-1", "10") should equal(true)
      r.incr("anshin-1") should equal(Some(11))
    }
    it("should reset to 0 and then increment by 1 for a key that contains a diff type") {
      r.set("anshin-2", "debasish") should equal(true)
      try {
        r.incr("anshin-2")
      } catch { case ex => ex.getMessage should equal("ERR value is not an integer") }
    }
    it("should increment by 5 for a key that contains a number") {
      r.set("anshin-3", "10") should equal(true)
      r.incrby("anshin-3", 5) should equal(Some(15))
    }
    it("should reset to 0 and then increment by 5 for a key that contains a diff type") {
      r.set("anshin-4", "debasish") should equal(true)
      try {
        r.incrby("anshin-4", 5)
      } catch { case ex => ex.getMessage should equal("ERR value is not an integer") }
    }
  }

  describe("decr") {
    it("should decrement by 1 for a key that contains a number") {
      r.set("anshin-1", "10") should equal(true)
      r.decr("anshin-1") should equal(Some(9))
    }
    it("should reset to 0 and then decrement by 1 for a key that contains a diff type") {
      r.set("anshin-2", "debasish") should equal(true)
      try {
        r.decr("anshin-2")
      } catch { case ex => ex.getMessage should equal("ERR value is not an integer") }
    }
    it("should decrement by 5 for a key that contains a number") {
      r.set("anshin-3", "10") should equal(true)
      r.decrby("anshin-3", 5) should equal(Some(5))
    }
    it("should reset to 0 and then decrement by 5 for a key that contains a diff type") {
      r.set("anshin-4", "debasish") should equal(true)
      try {
        r.decrby("anshin-4", 5)
      } catch { case ex => ex.getMessage should equal("ERR value is not an integer") }
    }
  }

  describe("mget") {
    it("should get values for existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
      r.set("anshin-3", "nilanjan") should equal(true)
      r.mget("anshin-1", "anshin-2", "anshin-3").get should equal(List(Some("debasish"), Some("maulindu"), Some("nilanjan")))
    }
    it("should give None for non-existing keys") {
      r.set("anshin-1", "debasish") should equal(true)
      r.set("anshin-2", "maulindu") should equal(true)
      r.mget("anshin-1", "anshin-2", "anshin-4").get should equal(List(Some("debasish"), Some("maulindu"), None))
    }
  }

  describe("mset") {
    it("should set all keys irrespective of whether they exist") {
      r.mset(
        ("anshin-1", "debasish"), 
        ("anshin-2", "maulindu"),
        ("anshin-3", "nilanjan")) should equal(true)
    }

    it("should set all keys only if none of them exist") {
      r.msetnx(
        ("anshin-4", "debasish"), 
        ("anshin-5", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(true)
      r.msetnx(
        ("anshin-7", "debasish"), 
        ("anshin-8", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(false)
      r.msetnx(
        ("anshin-4", "debasish"), 
        ("anshin-5", "maulindu"),
        ("anshin-6", "nilanjan")) should equal(false)
    }
  }

  describe("get with spaces in keys") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin software", "debasish ghosh") should equal(true)
      r.get("anshin software") match {
        case Some(s: String) => s should equal("debasish ghosh")
        case None => fail("should return debasish ghosh")
      }

      r.set("test key with spaces", "I am a value with spaces")
      r.get("test key with spaces").get should equal("I am a value with spaces")
    }
  }

  describe("get with newline values") {
    it("should retrieve key/value pairs for existing keys") {
      r.set("anshin-x", "debasish\nghosh\nfather") should equal(true)
      r.get("anshin-x") match {
        case Some(s: String) => s should equal("debasish\nghosh\nfather")
        case None => fail("should return debasish")
      }
    }
  }
}
