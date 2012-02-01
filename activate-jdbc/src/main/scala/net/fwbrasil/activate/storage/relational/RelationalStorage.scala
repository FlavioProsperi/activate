package net.fwbrasil.activate.storage.relational

import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.util.GraphUtil._
import net.fwbrasil.activate.util.Reflection.toNiceObject
import scala.collection.mutable.{ Map => MutableMap }

trait RelationalStorage extends MarshalStorage {

	override def store(insertMap: Map[Entity, Map[String, StorageValue]], updateMap: Map[Entity, Map[String, StorageValue]], deleteSet: Set[Entity]): Unit = {

		val inserts =
			for ((entity, propertyMap) <- insertMap)
				yield InsertDmlStorageStatement(entity.niceClass, entity.id, propertyMap.toMap)

		val insertsResolved = resolveDependencies(inserts.toSet)

		val updates =
			for ((entity, propertyMap) <- updateMap)
				yield UpdateDmlStorageStatement(entity.niceClass, entity.id, propertyMap.toMap)

		val deletes =
			for (entity <- deleteSet)
				yield DeleteDmlStorageStatement(entity.niceClass, entity.id, Map())

		val deletesResolved = resolveDependencies(deletes.toSet).reverse

		val sqls = insertsResolved ::: updates.toList ::: deletesResolved

		execute(sqls)
	}

	def resolveDependencies(statements: Set[DmlStorageStatement]): List[DmlStorageStatement] = {
		val entityInsertMap = statements.groupBy(_.entityId).mapValues(_.head)
		val tree = new DependencyTree[DmlStorageStatement](statements)
		for (insertA <- statements) {
			for ((propertyName, propertyValue) <- insertA.propertyMap)
				if (propertyValue.value != None && propertyValue.isInstanceOf[ReferenceStorageValue]) {
					val entityIdB = propertyValue.value.get.asInstanceOf[String]
					if (entityInsertMap.contains(entityIdB))
						tree.addDependency(entityInsertMap(entityIdB), insertA)
				}
		}
		val roots = tree.roots
		tree.resolve
	}

	def execute(sqls: List[DmlStorageStatement]): Unit

}