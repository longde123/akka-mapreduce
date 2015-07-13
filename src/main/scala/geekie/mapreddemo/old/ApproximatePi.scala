package geekie.mapreddemo.old

import akka.actor.{Actor, ActorSystem, Props}
import geekie.mapred.PipelineHelpers._
import geekie.mapred._

import scala.math.random

/**
 * Created by nlw on 16/04/15.
 * Bad stochastic Pi calculation with "loopy" flow control to print results.
 *
 */
object ApproximatePi extends App {
  println("APPROXIMATING PI")
  val system = ActorSystem("akka-wordcount")

  val wordcountSupervisor = system.actorOf(Props[PiMapReduceSupervisor], "wc-super")
  wordcountSupervisor ! StartCalculations
}


class PiMapReduceSupervisor extends Actor {

  type RedK = String
  type RedV = BigDecimal

  val nMappers = 4

  val myWorkers = PipelineStart[Int] map { zz =>
    val x = random * 2 - 1
    val y = random * 2 - 1
    Seq(
      KeyVal("SUM", BigDecimal(if (x * x + y * y < 1) 1 else 0)),
      KeyVal("N", BigDecimal(1))
    )
  } times nMappers reduce (_ + _) times 4 output self

  val mapper = myWorkers.head

  var progress = 0
  var finalAggregate: Map[RedK, RedV] = Map()

  def receive = {
    case StartCalculations =>
      println(s"STARTING MAPPERS")
      for (n <- 1 to nMappers)
        mapper ! Stream.continually(1).take(1000000 / nMappers).iterator
      mapper ! ForwardToReducer(EndOfData) // TODO: better flow control, every 1E6 iterations exactly

    case ReducerResult(agAny) =>
      val ag = agAny.asInstanceOf[Map[RedK, RedV]]
      finalAggregate = finalAggregate ++ ag

    case EndOfData =>
      PiPrintResults(finalAggregate)
      self ! StartCalculations
    // context.system.scheduler.scheduleOnce(1.second, self, PiMapReduceSupervisor.HammerdownProtocol)

    case PiMapReduceSupervisor.HammerdownProtocol => context.system.shutdown()
  }
}

object PiMapReduceSupervisor {
  case object HammerdownProtocol
}

object PiPrintResults {
  def apply[K, V](finalAggregate: Map[K, V]) = {
    val ag = finalAggregate.asInstanceOf[Map[String, BigDecimal]]
    val s = ag.getOrElse("SUM", BigDecimal(1))
    val n = ag.getOrElse("N", BigDecimal(2))
    println(s"Pi is roughly 4 * $s / $n ${4.0 * s / n}")
  }
}

case object StartCalculations