package net.fwbrasil.activate

import java.util.IdentityHashMap
import net.fwbrasil.radon.ref.Ref
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.EntityValidation
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.radon.transaction.NestedTransaction
import scala.collection.mutable.HashSet
import scala.collection.mutable.ListBuffer
import net.fwbrasil.activate.util.uuid.UUIDUtil
import net.fwbrasil.activate.coordinator.Coordinator
import net.fwbrasil.radon.ConcurrentTransactionException
import net.fwbrasil.radon.transaction.TransactionManager

class ActivateConcurrentTransactionException(val entitiesIds: Set[String], refs: Ref[_]*) extends ConcurrentTransactionException(refs: _*)

trait DurableContext {
	this: ActivateContext =>

	val contextId = UUIDUtil.generateUUID

	override protected[fwbrasil] val transactionManager =
		new TransactionManager()(this) {
			override protected def waitToRetry(e: ConcurrentTransactionException) = {
				e match {
					case e: ActivateConcurrentTransactionException =>
						reloadEntities(e.entitiesIds)
					case other =>
				}
				super.waitToRetry(e)
			}
		}

	protected lazy val coordinatorClientOption =
		Coordinator.clientOption(this)

	protected def reinitializeCoordinator = {
		coordinatorClientOption.map { coordinatorClient =>
			coordinatorClient.reinitialize
		}
	}

	protected def startCoordinator =
		coordinatorClientOption.map(coordinatorClient => {
			if (storage.isMemoryStorage)
				throw new IllegalStateException("Storage doesn't support coordinator")
		})

	private[activate] def reloadEntities(ids: Set[String]) = {
		liveCache.uninitialize(ids)
		coordinatorClientOption.get.removeNotifications(ids)
	}

	private def runWithCoordinatorIfDefined(reads: => Set[String], writes: => Set[String])(f: => Unit) =
		coordinatorClientOption.map { coordinatorClient =>

			val (readLocksNok, writeLocksNok) = coordinatorClient.tryToAcquireLocks(reads, writes)
			if (readLocksNok.nonEmpty || writeLocksNok.nonEmpty)
				throw new ActivateConcurrentTransactionException(readLocksNok ++ writeLocksNok)
			try
				f
			finally {
				val (readUnlocksNok, writeUnlocksNok) = coordinatorClient.releaseLocks(reads, writes)
				if (readUnlocksNok.nonEmpty || writeUnlocksNok.nonEmpty)
					throw new IllegalStateException("Can't release locks.")
			}

		}.getOrElse(f)

	override def makeDurable(transaction: Transaction) = {
		lazy val statements = statementsForTransaction(transaction)

		val (assignments, deletes) = filterVars(transaction.assignments)

		val assignmentsEntities = assignments.map(_._1.outerEntity)
		val deletedEntities = deletes.map(_._1)
		val entities = assignmentsEntities ::: deletedEntities

		lazy val reads = entities.map(_.id).toSet
		lazy val writes = transaction.reads.map(_.asInstanceOf[Var[_]].outerEntity.id).toSet

		runWithCoordinatorIfDefined(reads, writes) {
			if (assignments.nonEmpty || deletes.nonEmpty || statements.nonEmpty) {
				validateTransactionEnd(transaction, entities)
				storage.toStorage(statements.toList, assignments, deletes)
				setPersisted(assignmentsEntities)
				deleteFromLiveCache(deletedEntities)
				statements.clear
			}
		}
	}

	private[this] def setPersisted(entities: List[Entity]) =
		entities.foreach(_.setPersisted)

	private[this] def deleteFromLiveCache(entities: List[Entity]) =
		entities.foreach(liveCache.delete)

	private[this] def filterVars(pAssignments: List[(Ref[Any], Option[Any], Boolean)]) = {
		// Assume that all assignments are of Vars for performance reasons (could be Ref)
		val varAssignments = pAssignments.asInstanceOf[List[(Var[Any], Option[Any], Boolean)]]

		val (assignmentsDelete, assignmentsUpdate) = varAssignments.map(e => (e._1, e._1.tval(e._2), e._3)).partition(_._3)

		val deletes = new IdentityHashMap[Entity, ListBuffer[(Var[Any], EntityValue[Any])]]()

		for ((ref, value, destroyed) <- assignmentsDelete) {
			val entity = ref.outerEntity
			if (entity.isPersisted) {
				if (!deletes.containsKey(entity))
					deletes.put(entity, ListBuffer())
				deletes.get(entity) += (ref -> value)
			}
		}

		import scala.collection.JavaConversions._
		(assignmentsUpdate.map(e => (e._1, e._2)),
			deletes.toList.map(tuple => (tuple._1, tuple._2.toList)))
	}

	private def validateTransactionEnd(transaction: Transaction, entities: List[Entity]) = {
		val toValidate = entities.filter(EntityValidation.validatesOnTransactionEnd(_, transaction))
		if (toValidate.nonEmpty) {
			val nestedTransaction = new NestedTransaction(transaction)
			try transactional(nestedTransaction) {
				toValidate.foreach(_.validate)
			} finally
				nestedTransaction.rollback
		}
	}
}