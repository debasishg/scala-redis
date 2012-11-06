package com.redis

import serialization._
import scala.concurrent.{ ExecutionContext, Await, Future }
import scala.concurrent.duration._
import akka.actor.ActorSystem

/**
 * Implementing some of the common patterns like scatter/gather, which can benefit from
 * a non-blocking asynchronous mode of operations. We use the scala-redis blocking client 
 * along with connection pools and future based execution. In this example we use the 
 * future implementation of Twitter Finagle (http://github.com/twitter/finagle).
 *
 * Some figures are also available for these patterns in the corresponding test suites.
 * The test suite for this pattern is in PatternsSpec.scala and the figures when run on
 * an MBP quad core 8G are:
 *
 * ---------------------------------------------------------------------------------------
 * Operations per run: 400000 elapsed: 6.783825 ops per second: 58963.78518018964
 * Operations per run: 1000000 elapsed: 16.182655 ops per second: 61794.55719719663
 * Operations per run: 2000000 elapsed: 32.440666 ops per second: 46782.3752009831
 * ---------------------------------------------------------------------------------------
 */
object Patterns {
  def listPush(count: Int, key: String)(implicit clients: RedisClientPool) = { 
    clients.withClient { client =>
      (1 to count) foreach {i => client.rpush(key, i)}
      assert(client.llen(key) == Some(count))
    }
    key
  }

  def listPop(count: Int, key: String)(implicit clients: RedisClientPool) = {
    implicit val parseInt = Parse[Long](new String(_).toLong)
    clients.withClient { client =>
      val list = (1 to count) map {i => client.lpop[Long](key).get}
      assert(client.llen(key) == Some(0))
      list.sum
    }
  }

  // set up Executors
  val system = ActorSystem("ScatterGatherSystem")
  import system.dispatcher

  val timeout = 5 minutes

  private[this] def flow[A](noOfRecipients: Int, opsPerClient: Int, keyPrefix: String, 
    fn: (Int, String) => A) = {
    (1 to noOfRecipients) map {i => 
      Future {
        fn(opsPerClient, "list_" + i)
      }
    }
  }

  // scatter across clients and gather them to do a sum
  def scatterGatherWithList(opsPerClient: Int)(implicit clients: RedisClientPool) = {
    // scatter
    val futurePushes = flow(100, opsPerClient, "list_", listPush)

    // concurrent combinator: Future.sequence
    val allPushes = Future.sequence(futurePushes)

    // sequential combinator: flatMap
    val allSum = allPushes flatMap {result =>
      // gather
      val futurePops = flow(100, opsPerClient, "list_", listPop)
      val allPops = Future.sequence(futurePops)
      allPops map {members => members.sum}
    }
    Await.result(allSum, timeout).asInstanceOf[Long]
  }

  // scatter across clietns and gather the first future to complete
  def scatterGatherFirstWithList(opsPerClient: Int)(implicit clients: RedisClientPool) = {
    // scatter phase: push to 100 lists in parallel
    val futurePushes = flow(100, opsPerClient, "seq_", listPush)

    // wait for the first future to complete
    val firstPush = Future.firstCompletedOf(futurePushes)

    // do a sum on the list whose key we got from firstPush
    val firstSum = firstPush map {key =>
      listPop(opsPerClient, key)
    }
    Await.result(firstSum, timeout).asInstanceOf[Int]
  }
}
