package com.redis

import org.scalatest.Spec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.redis.RedisClient.{DESC, SUM}


@RunWith(classOf[JUnitRunner])
class SortedSetOperationsSpec extends Spec 
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

  import r._

  private def add = {
    zadd("hackers", 1965, "yukihiro matsumoto") should equal(Some(1))
    zadd("hackers", 1953, "richard stallman", (1916, "claude shannon"), (1969, "linus torvalds"), (1940, "alan kay"), (1912, "alan turing")) should equal(Some(5))
  }

  describe("zadd") {
    it("should add based on proper sorted set semantics") {
      add
      zadd("hackers", 1912, "alan turing") should equal(Some(0))
      zcard("hackers").get should equal(6)
    }
  }

  describe("zrem") {
    it("should remove") {
      add
      zrem("hackers", "alan turing") should equal(Some(1))
      zrem("hackers", "alan kay", "linus torvalds") should equal(Some(2))
      zrem("hackers", "alan kay", "linus torvalds") should equal(Some(0))
    }
  }

  describe("zrange") {
    it("should get the proper range") {
      add
      zrange("hackers").get should have size (6)
      zrangeWithScore("hackers").get should have size(6)
    }
  }

  describe("zrank") {
    it ("should give proper rank") {
      add
      zrank("hackers", "yukihiro matsumoto") should equal(Some(4))
      zrank("hackers", "yukihiro matsumoto", reverse = true) should equal(Some(1))
    }
  }

  describe("zremrangebyrank") {
    it ("should remove based on rank range") {
      add
      zremrangebyrank("hackers", 0, 2) should equal(Some(3))
    }
  }

  describe("zremrangebyscore") {
    it ("should remove based on score range") {
      add
      zremrangebyscore("hackers", 1912, 1940) should equal(Some(3))
      zremrangebyscore("hackers", 0, 3) should equal(Some(0))
    }
  }

  describe("zunion") {
    it ("should do a union") {
      zadd("hackers 1", 1965, "yukihiro matsumoto") should equal(Some(1))
      zadd("hackers 1", 1953, "richard stallman") should equal(Some(1))
      zadd("hackers 2", 1916, "claude shannon") should equal(Some(1))
      zadd("hackers 2", 1969, "linus torvalds") should equal(Some(1))
      zadd("hackers 3", 1940, "alan kay") should equal(Some(1))
      zadd("hackers 4", 1912, "alan turing") should equal(Some(1))

      // union with weight = 1
      zunionstore("hackers", List("hackers 1", "hackers 2", "hackers 3", "hackers 4"), SUM) should equal(Some(6))
      zcard("hackers") should equal(Some(6))

      zrangeWithScore("hackers").get.map(_._2) should equal(List(1912, 1916, 1940, 1953, 1965, 1969))

      // union with modified weights
      zunionstoreWeighted("hackers weighted", Map("hackers 1" -> 1.0, "hackers 2" -> 2.0, "hackers 3" -> 3.0, "hackers 4" -> 4.0), SUM) should equal(Some(6))
      zrangeWithScore("hackers weighted").get.map(_._2.toInt) should equal(List(1953, 1965, 3832, 3938, 5820, 7648))
    }
  }
  
  describe("zcount") {
    it ("should return the number of elements between min and max") {
      add
      
      zcount("hackers", 1912, 1920) should equal(Some(2))
    }
  }

  describe("zrangebyscore") {
    it ("should return the elements between min and max") {
      add

      zrangebyscore("hackers", 1940, true, 1969, true, None).get should equal(
        List("alan kay", "richard stallman", "yukihiro matsumoto", "linus torvalds"))

      zrangebyscore("hackers", 1940, true, 1969, true, None, DESC).get should equal(
        List("linus torvalds", "yukihiro matsumoto", "richard stallman","alan kay"))
    }

    it("should return the elements between min and max and allow offset and limit") {
      add

      zrangebyscore("hackers", 1940, true, 1969, true, Some(0, 2)).get should equal(
        List("alan kay", "richard stallman"))

      zrangebyscore("hackers", 1940, true, 1969, true, Some(0, 2), DESC).get should equal(
        List("linus torvalds", "yukihiro matsumoto"))

      zrangebyscore("hackers", 1940, true, 1969, true, Some(3, 1)).get should equal (
        List("linus torvalds"))

      zrangebyscore("hackers", 1940, true, 1969, true, Some(3, 1), DESC).get should equal (
        List("alan kay"))

      zrangebyscore("hackers", 1940, false, 1969, true, Some(0, 2)).get should equal (
        List("richard stallman", "yukihiro matsumoto"))

      zrangebyscore("hackers", 1940, true, 1969, false, Some(0, 2), DESC).get should equal (
        List("yukihiro matsumoto", "richard stallman"))
    }
  }
}
