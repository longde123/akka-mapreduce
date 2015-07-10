package geekie.mapred

import akka.actor.{Actor, ActorRef}
import geekie.mapred.io.{DataChunk, FileChunkLineReader, FileChunk}

import scala.language.existentials
import scala.reflect.ClassTag

/**
 * Created by nlw on 08/04/15.
 * A mapper that takes an object (iterator) and forwards zero or more corresponding calculated objects to the reducer.
 *
 */
class MapperTask[A: ClassTag, B](output: ActorRef, f: A => TraversableOnce[B], lazyMap: Boolean = false) extends Actor {
  def receive = {
    case datum: A =>
      if (lazyMap) output ! f(datum)
      else f(datum) foreach (output ! _)

    case dataItr: TraversableOnce[A] =>
      if (lazyMap) output ! (dataItr flatMap f)
      else dataItr flatMap f foreach (output ! _)

    case DataChunk(chunk: TraversableOnce[A], n, limit) =>
      val dataItr = if (limit.isDefined) chunk.toIterator.take(limit.get) else chunk.toIterator
      val kvs = dataItr flatMap f
      output ! KeyValTraversable(kvs.asInstanceOf[TraversableOnce[KeyVal[K, V]] forSome {type K; type V}])
      output ! ProgressReport(n)

    case FileChunk(chunk: FileChunkLineReader, n, limit) =>
      try {
        val dataItr = if (limit.isDefined) chunk.iterator.take(limit.get) else chunk.iterator
        dataItr flatMap f.asInstanceOf[String => TraversableOnce[B]] foreach (output ! _)
        output ! ProgressReport(n)
      } finally {
        chunk.close()
      }

    case Forward(x) => output forward x
  }
}


object MapperTask {
  def apply[A: ClassTag, B](output: ActorRef, lazyMap: Boolean = false)(f: A => TraversableOnce[B]) = new MapperTask(output, f, lazyMap = lazyMap)

  case class LazyMap[A](dataItr: TraversableOnce[A])
}
