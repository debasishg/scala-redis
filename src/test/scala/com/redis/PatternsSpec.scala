package com.redis

import org.scalatest.FunSpec
import org.scalatest.BeforeAndAfterEach
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith

import Patterns._

@RunWith(classOf[JUnitRunner])
class PatternsSpec extends FunSpec 
               with ShouldMatchers
               with BeforeAndAfterEach
               with BeforeAndAfterAll {

  implicit val clients = new RedisClientPool("localhost", 6379)

  override def beforeEach = {
  }

  override def afterEach = clients.withClient{
    client => client.flushdb
  }

  override def afterAll = {
    clients.withClient{ client => client.disconnect }
    clients.close
  }

  def runScatterGather(opsPerRun: Int) = {
    val start = System.nanoTime
    val sum = scatterGatherWithList(opsPerRun)
    assert(sum == (1L to opsPerRun).sum * 100L)
    val elapsed: Double = (System.nanoTime - start) / 1000000000.0
    val opsPerSec: Double = (100 * opsPerRun * 2) / elapsed
    println("Operations per run: " + opsPerRun * 100 * 2 + " elapsed: " + elapsed + " ops per second: " + opsPerSec)
  }

  def runScatterGatherFirst(opsPerRun: Int) = {
    val start = System.nanoTime
    val sum = scatterGatherFirstWithList(opsPerRun)
    assert(sum == (1 to opsPerRun).sum)
    val elapsed: Double = (System.nanoTime - start) / 1000000000.0
    val opsPerSec: Double = (101 * opsPerRun) / elapsed
    println("Operations per run: " + opsPerRun * 101 + " elapsed: " + elapsed + " ops per second: " + opsPerSec)
  }

  describe("scatter/gather with list test 1") {
    it("should distribute work amongst the clients") {
      runScatterGather(2000)
    }
  }

  describe("scatter/gather with list test 2") {
    it("should distribute work amongst the clients") {
      runScatterGather(5000)
    }
  }

  describe("scatter/gather with list test 3") {
    it("should distribute work amongst the clients") {
      runScatterGather(10000)
    }
  }

  describe("scatter/gather first with list test 1") {
    it("should distribute work amongst the clients") {
      runScatterGatherFirst(5000)
    }
  }
}
