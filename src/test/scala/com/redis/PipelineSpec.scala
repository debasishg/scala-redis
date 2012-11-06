package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class PipelineSpec extends FunSpec 
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

  describe("pipeline1") {
    it("should do pipelined commands") {
      r.pipeline { p =>
        p.set("key", "debasish")
        p.get("key")
        p.get("key1")
      }.get should equal(List(true, Some("debasish"), None))
    }
  }

  describe("pipeline2") {
    it("should do pipelined commands") {
      r.pipeline { p =>
        p.lpush("country_list", "france")
        p.lpush("country_list", "italy")
        p.lpush("country_list", "germany")
        p.incrby("country_count", 3)
        p.lrange("country_list", 0, -1)
      }.get should equal (List(Some(1), Some(2), Some(3), Some(3), Some(List(Some("germany"), Some("italy"), Some("france")))))
    }
  }

  describe("pipeline3") {
    it("should handle errors properly in pipelined commands") {
      val thrown = 
        evaluating {
          r.pipeline { p =>
            p.set("a", "abc")
            p.lpop("a")
          }
        } should produce [Exception]
      thrown.getMessage should equal ("ERR Operation against a key holding the wrong kind of value")
      r.get("a").get should equal("abc")
    }
  }

  describe("pipeline4") {
    it("should discard pipelined commands") {
      r.pipeline { p =>
        p.set("a", "abc")
        throw new RedisMultiExecException("want to discard")
      } should equal(None)
      r.get("a") should equal(None)
    }
  }
}
