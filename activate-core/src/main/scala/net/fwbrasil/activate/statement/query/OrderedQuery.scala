package net.fwbrasil.activate.statement.query

import org.joda.time.base.AbstractInstant
import scala.collection.immutable.TreeSet
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.StatementSelectValue
import scala.annotation.implicitNotFound
import scala.math.Ordering.OptionOrdering

trait OrderedQueryContext {

	implicit def abstractInstantOrdering[A <: AbstractInstant]: Ordering[A] = new Ordering[A] {
		def compare(x: A, y: A) = x.toDate.compareTo(y.toDate)
	}

	implicit def toOrderByCriteria[T](value: T)(implicit tval: (=> T) => StatementSelectValue[T], ordering: Ordering[T]) =
		OrderByCriteria[T](value, orderByAscendingDirection, ordering)

	implicit def toOrderByDirectionWrapper[T](value: T)(implicit tval: (=> T) => StatementSelectValue[T], ordering: Ordering[T]) =
		OrderByDirectionWrapper[T](value)

	implicit def toOrderByWrapper[S](query: Query[S]) =
		OrderByWrapper(query)
}

case class OrderByWrapper[S](query: Query[S]) {

	def orderBy(criterias: OrderByCriteria[_]*): Query[S] =
		if (!criterias.isEmpty)
			new OrderedQuery[S](query.from, query.where, query.select, OrderBy(criterias: _*))
		else query
}

class OrderedQuery[S](override val from: From, override val where: Where, override val select: Select, val _orderBy: OrderBy)
		extends Query[S](from, where, select) {
	override def orderByClause = Some(_orderBy)
	override def toString = super.toString + _orderBy.toString

	override def productElement(n: Int): Any =
		n match {
			case 0 => from
			case 1 => where
			case 2 => select
			case 3 => _orderBy
		}
	override def productArity: Int = 4
	override def canEqual(that: Any): Boolean =
		that.getClass == classOf[OrderedQuery[S]]
	override def equals(that: Any): Boolean =
		canEqual(that) &&
			that.asInstanceOf[OrderedQuery[S]]._orderBy == _orderBy
}

case class OrderBy(criterias: OrderByCriteria[_]*) {
	def ordering[S] = new Ordering[S] {
		def compare(x: S, y: S) = {
			val tuple1 = x.asInstanceOf[Product]
			val tuple2 = y.asInstanceOf[Product]
			val tuplesArity = tuple1.productArity
			val criteriasSize = criterias.size
			var result = 0
			val tupleStartPos = tuplesArity - criteriasSize
			val stream = (tupleStartPos until tuplesArity).toStream
			stream.takeWhile((i: Int) => result == 0).foreach { (i: Int) =>
				val a = tuple1.productElement(i)
				val b = tuple2.productElement(i)
				val criteria = criterias(i - tupleStartPos)
				val direction = criteria.direction
				val (x, y) =
					if (direction == orderByAscendingDirection)
						(a, b)
					else
						(b, a)
				result =
					if (x == null && y != null)
						-1
					else if (x != null && y == null)
						1
					else if (x == null && y == null)
						0
					else {
						val ordering = criteria.ordering
						ordering match {
							case ordering: OptionOrdering[Any] =>
								ordering.compare(Option(x), Option(y))
							case ordering =>
								ordering.asInstanceOf[Ordering[Any]].compare(x, y)
						}
					}
			}
			0
			result
		}
	}
	override def toString = " orderBy (" + criterias.mkString(", ") + ")"
}

abstract sealed class OrderByDirection
case object orderByAscendingDirection extends OrderByDirection {
	override def toString = "asc"
}
case object orderByDescendingDirection extends OrderByDirection {
	override def toString = "desc"
}

@implicitNotFound("Can't find a Ordering implicit. Maybe the type does not support ordering.")
case class OrderByDirectionWrapper[T](value: StatementSelectValue[T])(implicit ordering: Ordering[T]) {
	def asc =
		OrderByCriteria[T](value, orderByAscendingDirection, ordering)
	def desc =
		OrderByCriteria[T](value, orderByDescendingDirection, ordering)
}

case class OrderByCriteria[T](value: StatementSelectValue[T], direction: OrderByDirection, ordering: Ordering[T]) {
	//	def ordering =
	//		if (direction == orderByAscendingDirection)
	//			_ordering
	//		else
	//			_ordering.reverse
	override def toString = value.toString() + " " + direction.toString
}