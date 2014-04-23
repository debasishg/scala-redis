package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith


@RunWith(classOf[JUnitRunner])
class PipelineSpec extends FunSpec 
                   with Matchers
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
      val thrown = the [Exception] thrownBy {
          r.pipeline { p =>
            p.set("a", "abc")
            p.lpop("a")
          }
        }
      thrown.getMessage should equal ("WRONGTYPE Operation against a key holding the wrong kind of value")
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

  import scala.concurrent.ExecutionContext.Implicits.global
  import scala.concurrent.{Await, Future}
  import scala.util.Success
  import scala.concurrent.duration._

  describe("pipeline no multi 1") {
    it("should execute 100 lpushes in pipeline") {

      val timeout = 2 minutes

      val vs = List.range(0, 100)
      import com.redis.serialization.Parse.Implicits.parseInt
      val x = r.pipelineNoMulti(vs.map(a => {() => r.lpush("list", a)}))

      x.foreach{a => Await.result(a.future, timeout)}
      r.lrange[Int]("list", 0, 100).get.map(_.get).reverse should equal(vs)
    }
  }

  describe("pipeline no multi 2") {
    it("should do pipelined commands with an exception") {

      val timeout = 2 minutes

      val x =
      r.pipelineNoMulti(
        List(
          {() => r.set("key", "debasish")},
          {() => r.get("key")},
          {() => r.get("key1")},
          {() => r.lpush("list", "maulindu")},
          {() => r.lpush("key", "maulindu")}     // should raise an exception
        )
      )

      val result = x.map{a => Await.result(a.future, timeout)}
      result.head should equal(true)
      result.last.isInstanceOf[Exception] should be (true)
    }
  }
}
