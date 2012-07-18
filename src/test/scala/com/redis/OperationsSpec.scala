package com.redis

import org.scalatest.Spec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class OperationsSpec extends Spec 
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

  describe("keys") {
    it("should fetch keys") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.keys("anshin*") match {
        case Some(s: List[Option[String]]) => s.size should equal(2)
        case None => fail("should have 2 elements")
      }
    }

    it("should fetch keys with spaces") {
      r.set("anshin 1", "debasish")
      r.set("anshin 2", "maulindu")
      r.keys("anshin*") match {
        case Some(s: List[Option[String]]) => s.size should equal(2)
        case None => fail("should have 2 elements")
      }
    }
  }

  describe("randomkey") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.randkey match {
        case Some(s: String) => s should startWith("anshin") 
        case None => fail("should have 2 elements")
      }
    }
  }

  describe("rename") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.rename("anshin-2", "anshin-2-new") should equal(true)
      val thrown = evaluating { r.rename("anshin-2", "anshin-2-new") } should produce[Exception]
      thrown.getMessage should equal ("ERR no such key")
    }
  }

  describe("renamenx") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.renamenx("anshin-2", "anshin-2-new") should equal(true)
      r.renamenx("anshin-1", "anshin-2-new") should equal(false)
    }
  }

  describe("dbsize") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.dbsize.get should equal(2)
    }
  }

  describe("exists") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.exists("anshin-2") should equal(true)
      r.exists("anshin-1") should equal(true)
      r.exists("anshin-3") should equal(false)
    }
  }

  describe("del") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.del("anshin-2", "anshin-1").get should equal(2)
      r.del("anshin-2", "anshin-1").get should equal(0)
    }
  }

  describe("type") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.getType("anshin-2").get should equal("string")
    }
  }

  describe("expire") {
    it("should give") {
      r.set("anshin-1", "debasish")
      r.set("anshin-2", "maulindu")
      r.expire("anshin-2", 1000) should equal(true)
      r.ttl("anshin-2") should equal(Some(1000))
      r.expire("anshin-3", 1000) should equal(false)
    }
  }
}
