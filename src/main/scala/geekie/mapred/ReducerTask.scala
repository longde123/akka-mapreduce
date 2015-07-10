package geekie.mapred

import akka.actor.{Actor, ActorRef}

import scala.reflect.ClassTag

/**
 * Created by nlw on 08/04/15.
 * A reducer that aggregates objects associated to a certain key in a Map.
 *
 */
class ReducerTask[K: ClassTag, V: ClassTag](output: ActorRef, f: (V, V) => V) extends Actor {

  var aggregator: Map[K, V] = Map()

  def updateAggregator(key: K, value: V) = {
    val newValue = if (aggregator contains key) f(aggregator(key), value) else value
    aggregator += (key -> newValue)
  }

  def receive = {
    case KeyVal(key: Any, value: V) =>
      updateAggregator(key.asInstanceOf[K], value)

    case kvs: TraversableOnce[Any] =>
      for (KeyVal(k, v) <- kvs.asInstanceOf[TraversableOnce[KeyVal[K, V]]])
        updateAggregator(k.asInstanceOf[K], v.asInstanceOf[V])

    case GetAggregator =>
      output ! ReducerResult(aggregator)
      context stop self

    case Forward(x) => output forward x
  }
}

object ReducerTask {
  def apply[K: ClassTag, V: ClassTag](output: ActorRef)(f: (V, V) => V) = new ReducerTask[K, V](output, f)
}
