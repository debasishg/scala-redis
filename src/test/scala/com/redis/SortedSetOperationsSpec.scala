package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import com.redis.RedisClient.{DESC, SUM}


@RunWith(classOf[JUnitRunner])
class SortedSetOperationsSpec extends FunSpec 
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

  import r._

  private def add = {
    zadd("hackers", 1965, "yukihiro matsumoto") should equal(Some(1))
    zadd("hackers", 1953, "richard stallman", (1916, "claude shannon"), (1969, "linus torvalds"), (1940, "alan kay"), (1912, "alan turing")) should equal(Some(5))
  }

  private def addWithoutScore = {
    zadd("autocomplete", 0d, "one") should equal(Some(1))
    zadd("autocomplete", 0d, "two", (0d, "three"), (0d, "four"), (0d, "five"), (0d, "six"))
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
      zunionstore("hackers", List("hackers 1", "hackers 2", "hackers 3", "hackers 4")) should equal(Some(6))
      zcard("hackers") should equal(Some(6))

      zrangeWithScore("hackers").get.map(_._2) should equal(List(1912, 1916, 1940, 1953, 1965, 1969))

      // union with modified weights
      zunionstoreWeighted("hackers weighted", Map("hackers 1" -> 1.0, "hackers 2" -> 2.0, "hackers 3" -> 3.0, "hackers 4" -> 4.0)) should equal(Some(6))
      zrangeWithScore("hackers weighted").get.map(_._2.toInt) should equal(List(1953, 1965, 3832, 3938, 5820, 7648))
    }
  }

  describe("zinter") {
    it ("should do an intersection") {
      zadd("hackers", 1912, "alan turing") should equal(Some(1))
      zadd("hackers", 1916, "claude shannon") should equal(Some(1))
      zadd("hackers", 1927, "john mccarthy") should equal(Some(1))
      zadd("hackers", 1940, "alan kay") should equal(Some(1))
      zadd("hackers", 1953, "richard stallman") should equal(Some(1))
      zadd("hackers", 1954, "larry wall") should equal(Some(1))
      zadd("hackers", 1956, "guido van rossum") should equal(Some(1))
      zadd("hackers", 1965, "paul graham") should equal(Some(1))
      zadd("hackers", 1965, "yukihiro matsumoto") should equal(Some(1))
      zadd("hackers", 1969, "linus torvalds") should equal(Some(1))

      zadd("baby boomers", 1948, "phillip bobbit") should equal(Some(1))
      zadd("baby boomers", 1953, "richard stallman") should equal(Some(1))
      zadd("baby boomers", 1954, "cass sunstein") should equal(Some(1))
      zadd("baby boomers", 1954, "larry wall") should equal(Some(1))
      zadd("baby boomers", 1956, "guido van rossum") should equal(Some(1))
      zadd("baby boomers", 1961, "lawrence lessig") should equal(Some(1))
      zadd("baby boomers", 1965, "paul graham") should equal(Some(1))
      zadd("baby boomers", 1965, "yukihiro matsumoto") should equal(Some(1))

      // intersection with weight = 1
      zinterstore("baby boomer hackers", List("hackers", "baby boomers")) should equal(Some(5))
      zcard("baby boomer hackers") should equal(Some(5))

      zrange("baby boomer hackers").get should equal(List("richard stallman", "larry wall", "guido van rossum", "paul graham", "yukihiro matsumoto"))

      // intersection with modified weights
      zinterstoreWeighted("baby boomer hackers weighted", Map("hackers" -> 0.5, "baby boomers" -> 0.5)) should equal(Some(5))
      zrangeWithScore("baby boomer hackers weighted").get.map(_._2.toInt) should equal(List(1953, 1954, 1956, 1965, 1965))
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

  describe("zrangebyscoreWithScore") {
    it ("should return the elements between min and max") {
      add

      zrangebyscoreWithScore("hackers", 1940, true, 1969, true, None).get should equal(
        List(("alan kay", 1940.0), ("richard stallman", 1953.0), ("yukihiro matsumoto", 1965.0), ("linus torvalds", 1969.0)))

      zrangebyscoreWithScore("hackers", 1940, true, 1969, true, None, DESC).get should equal(
        List(("linus torvalds", 1969.0), ("yukihiro matsumoto", 1965.0), ("richard stallman", 1953.0),("alan kay", 1940.0)))

      zrangebyscoreWithScore("hackers", 1940, true, 1969, true, Some(3, 1)).get should equal (
        List(("linus torvalds", 1969.0)))

      zrangebyscoreWithScore("hackers", 1940, true, 1969, true, Some(3, 1), DESC).get should equal (
        List(("alan kay", 1940.0)))
    }
  }
  describe("zrangebylex") {
    it("should return the elements between - and +") {
      addWithoutScore

      zrangebylex("autocomplete").get should equal(
        List("five", "four", "one", "six", "three", "two"))

      zrangebylex("autocomplete", "-", true, "+", true).get should equal(
        List("five", "four", "one", "six", "three", "two"))

      zrangebylex("autocomplete", "-", false, "+", false).get should equal(
        List("five", "four", "one", "six", "three", "two"))
    }

    it("should return the elements between the two strings and allow offset and limit") {
      addWithoutScore

      zrangebylex("autocomplete", "f", true, "fz", true, Some(0, 2)).get should equal(
        List("five", "four"))

      zrangebylex("autocomplete", "f", true, "fz", true, Some(1, 1)).get should equal(
        List("four"))

    }
  }
}
