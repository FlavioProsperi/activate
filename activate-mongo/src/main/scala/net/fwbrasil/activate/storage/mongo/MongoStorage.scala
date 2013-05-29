package net.fwbrasil.activate.storage.mongo

import java.util.Date
import java.util.IdentityHashMap
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import com.mongodb.BasicDBList
import com.mongodb.BasicDBObject
import com.mongodb.DB
import com.mongodb.DBCollection
import com.mongodb.DBCursor
import com.mongodb.DBObject
import com.mongodb.Mongo
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.EntityHelper.getEntityName
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.And
import net.fwbrasil.activate.statement.BooleanOperatorCriteria
import net.fwbrasil.activate.statement.CompositeOperator
import net.fwbrasil.activate.statement.CompositeOperatorCriteria
import net.fwbrasil.activate.statement.Criteria
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.IsEqualTo
import net.fwbrasil.activate.statement.IsNotEqualTo
import net.fwbrasil.activate.statement.IsGreaterOrEqualTo
import net.fwbrasil.activate.statement.IsGreaterThan
import net.fwbrasil.activate.statement.IsLessOrEqualTo
import net.fwbrasil.activate.statement.IsLessThan
import net.fwbrasil.activate.statement.IsNotNull
import net.fwbrasil.activate.statement.IsNull
import net.fwbrasil.activate.statement.Matcher
import net.fwbrasil.activate.statement.Or
import net.fwbrasil.activate.statement.SimpleOperatorCriteria
import net.fwbrasil.activate.statement.SimpleStatementBooleanValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.SimpleValue
import net.fwbrasil.activate.statement.StatementBooleanValue
import net.fwbrasil.activate.statement.StatementEntityInstanceValue
import net.fwbrasil.activate.statement.StatementEntitySourcePropertyValue
import net.fwbrasil.activate.statement.StatementEntitySourceValue
import net.fwbrasil.activate.statement.StatementSelectValue
import net.fwbrasil.activate.statement.StatementValue
import net.fwbrasil.activate.statement.Where
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.storage.marshalling._
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.util.RichList._
import com.mongodb.MongoClient
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.query.OrderedQuery
import net.fwbrasil.activate.statement.query.orderByAscendingDirection
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import net.fwbrasil.activate.statement.ToUpperCase
import net.fwbrasil.activate.statement.ToLowerCase

trait MongoStorage extends MarshalStorage[DB] with DelayedInit {

    val host: String
    val port: Int = 27017
    val db: String
    val authentication: Option[(String, String)] = None

    def directAccess =
        mongoDB

    private var mongoDB: DB = _

    override def delayedInit(body: => Unit) = {
        body
        val conn = new MongoClient(host, port)
        mongoDB = conn.getDB(db)
        if (authentication.isDefined) {
            val (user, password) = authentication.get
            mongoDB.authenticate(user, password.toArray[Char])
        }
        mongoDB
    }

    def isMemoryStorage = false
    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = false

    override def store(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        preVerifyStaleData(updateList ++ deleteList)
        storeStatements(statements)
        storeInserts(insertList)
        storeUpdates(updateList)
        storeDeletes(deleteList)

        None
    }

    private def preVerifyStaleData(
        data: List[(Entity, Map[String, StorageValue])]) = {
        val invalid =
            data.filter(_._2.contains(versionVarName)).filterNot { tuple =>
                val (entity, properties) = tuple
                val query = new BasicDBObject
                query.put("_id", entity.id)
                addVersionCondition(query, properties)
                val result = coll(entity).find(query).count
                result == 1
            }
        if (invalid.nonEmpty)
            staleDataException(invalid.map(_._1.id).toSet)
    }

    private def addVersionCondition(query: BasicDBObject, properties: Map[String, StorageValue]) =
        if (properties.contains(versionVarName)) {
            val nullVersion = new BasicDBObject
            nullVersion.put(versionVarName, null)
            val versionValue = new BasicDBObject
            versionValue.put(versionVarName, getMongoValue(properties(versionVarName)) match {
                case value: Long =>
                    value - 1l
            })
            val versionQuery = new BasicDBList
            versionQuery.add(nullVersion)
            versionQuery.add(versionValue)
            query.put("$or", versionQuery)
        }

    private def storeDeletes(deleteList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- deleteList) {
            val query = new BasicDBObject()
            query.put("_id", entity.id)
            addVersionCondition(query, properties)
            val result = coll(entity).remove(query)
            if (result.getN != 1)
                staleDataException(Set(entity.id))
        }

    private def storeUpdates(updateList: List[(Entity, Map[String, StorageValue])]) =
        for ((entity, properties) <- updateList) {
            val query = new BasicDBObject
            query.put("_id", entity.id)
            val set = new BasicDBObject
            for ((name, value) <- properties if (name != "id")) {
                val inner = new BasicDBObject
                set.put(name, getMongoValue(value))
            }
            val update = new BasicDBObject
            update.put("$set", set)
            addVersionCondition(query, properties)
            val result = coll(entity).update(query, update)
            if (result.getN != 1)
                staleDataException(Set(entity.id))
        }

    private def storeInserts(insertList: List[(Entity, Map[String, StorageValue])]) = {
        val insertMap = new IdentityHashMap[Class[_], ListBuffer[BasicDBObject]]()
        for ((entity, properties) <- insertList) {
            val doc = new BasicDBObject()
            for ((name, value) <- properties if (name != "id"))
                doc.put(name, getMongoValue(value))
            doc.put("_id", entity.id)
            insertMap.getOrElseUpdate(entity.getClass, ListBuffer()) += doc
        }
        for (entityClass <- insertMap.keys)
            coll(entityClass).insert(insertMap(entityClass))
    }

    private def storeStatements(statements: List[MassModificationStatement]) =
        for (statement <- statements) {
            val (coll, where) = collectionAndWhere(statement.from, statement.where)
            statement match {
                case update: MassUpdateStatement =>
                    val set = new BasicDBObject
                    for (assignment <- update.assignments)
                        set.put(mongoStatementSelectValue(assignment.assignee), getMongoValue(assignment.value))
                    val mongoUpdate = new BasicDBObject
                    mongoUpdate.put("$set", set)
                    coll.updateMulti(where, mongoUpdate)
                case delete: MassDeleteStatement =>
                    coll.remove(where)
            }
        }

    private def getMongoValue(value: StorageValue): Any =
        value match {
            case value: IntStorageValue =>
                value.value.map(_.intValue).getOrElse(null)
            case value: LongStorageValue =>
                value.value.map(_.longValue).getOrElse(null)
            case value: BooleanStorageValue =>
                value.value.map(_.booleanValue).getOrElse(null)
            case value: StringStorageValue =>
                value.value.getOrElse(null)
            case value: FloatStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: DateStorageValue =>
                value.value.getOrElse(null)
            case value: DoubleStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: BigDecimalStorageValue =>
                value.value.map(_.doubleValue).getOrElse(null)
            case value: ListStorageValue =>
                value.value.map { list =>
                    val dbList = new BasicDBList()
                    list.foreach(elem => dbList.add(getMongoValue(elem).asInstanceOf[Object]))
                    dbList
                }.orNull
            case value: ByteArrayStorageValue =>
                value.value.getOrElse(null)
            case value: ReferenceStorageValue =>
                value.value.getOrElse(null)
        }

    private[this] def coll(entity: Entity): DBCollection =
        coll(entity.getClass)

    private[this] def coll(entityClass: Class[_]): DBCollection =
        coll(getEntityName(entityClass))

    private[this] def coll(entityName: String): DBCollection =
        mongoDB.getCollection(entityName)

    override def query(queryInstance: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] = {
        val from = queryInstance.from
        val (coll, where) = collectionAndWhere(from, queryInstance.where, entitiesReadFromCache)
        val selectValues = queryInstance.select.values
        val select = querySelect(queryInstance, selectValues)
        val ret = coll.find(where, select)
        orderQueryIfNecessary(queryInstance, ret)
        limitQueryIfNecessary(queryInstance, ret)
        transformResultToTheExpectedTypes(expectedTypes, selectValues, ret)
    }

    def getStorageValue(obj: Any, storageValue: StorageValue): StorageValue = {
        def getValue[T] = Option(obj.asInstanceOf[T])
        storageValue match {
            case value: IntStorageValue =>
                IntStorageValue(getValue[Int])
            case value: LongStorageValue =>
                LongStorageValue(getValue[Long])
            case value: BooleanStorageValue =>
                BooleanStorageValue(getValue[Boolean])
            case value: StringStorageValue =>
                StringStorageValue(getValue[String])
            case value: FloatStorageValue =>
                FloatStorageValue(getValue[Double].map(_.floatValue))
            case value: DateStorageValue =>
                DateStorageValue(getValue[Date])
            case value: DoubleStorageValue =>
                DoubleStorageValue(getValue[Double])
            case value: BigDecimalStorageValue =>
                BigDecimalStorageValue(getValue[Double].map(BigDecimal(_)))
            case value: ListStorageValue =>
                ListStorageValue(getValue[BasicDBList].map { dbList =>
                    dbList.map(elem => getStorageValue(elem, value.emptyStorageValue)).toList
                }, value.emptyStorageValue)
            case value: ByteArrayStorageValue =>
                ByteArrayStorageValue(getValue[Array[Byte]])
            case value: ReferenceStorageValue =>
                ReferenceStorageValue(getValue[String])
        }
    }

    def getValue(obj: DBObject, name: String, storageValue: StorageValue): StorageValue =
        getStorageValue(obj.get(name), storageValue)

    def getValue[T](obj: DBObject, name: String) =
        Option(obj.get(name).asInstanceOf[T])

    def query(values: StatementSelectValue[_]*): Seq[String] =
        for (value <- values)
            yield mongoStatementSelectValue(value)

    def mongoStatementSelectValue(value: StatementSelectValue[_]): String =
        value match {
            case value: ToUpperCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue[_] =>
                val name = value.propertyPathNames.onlyOne
                if (name == "id")
                    "_id"
                else
                    name
            case value: StatementEntitySourceValue[_] =>
                "_id"
            case other =>
                throw new UnsupportedOperationException("Mongo storage supports only entity properties inside select clause.")
        }

    def query(criteria: Criteria): DBObject = {
        val obj = new BasicDBObject
        criteria match {
            case criteria: BooleanOperatorCriteria =>
                val list = new BasicDBList
                list.add(query(criteria.valueA))
                list.add(query(criteria.valueB))
                val operator = query(criteria.operator)
                obj.put(operator, list)
                obj
            case criteria: CompositeOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value = getMongoValue(criteria.valueB)
                if (criteria.operator.isInstanceOf[IsEqualTo])
                    obj.put(property, value)
                else {
                    val operator = query(criteria.operator)
                    val innerObj = new BasicDBObject
                    innerObj.put(operator, value)
                    obj.put(property, innerObj)
                }
                obj
            case criteria: SimpleOperatorCriteria =>
                val property = queryEntityProperty(criteria.valueA)
                val value = criteria.operator match {
                    case value: IsNull =>
                        null
                    case value: IsNotNull =>
                        val temp = new BasicDBObject
                        temp.put("$ne", null)
                        temp
                }
                obj.put(property, value)
                obj
        }
    }

    def getMongoValue(value: StatementValue): Any =
        value match {
            case value: SimpleStatementBooleanValue =>
                getMongoValue(Marshaller.marshalling(value.value))
            case value: SimpleValue[_] =>
                getMongoValue(Marshaller.marshalling(value.entityValue))
            case value: StatementEntityInstanceValue[_] =>
                getMongoValue(StringStorageValue(Option(value.entityId)))
            case null =>
                null
            case other =>
                throw new UnsupportedOperationException("Mongo storage doesn't support joins.")
        }

    def query(value: StatementBooleanValue): DBObject =
        value match {
            case value: Criteria =>
                query(value)
            case value: SimpleStatementBooleanValue =>
                val list = new BasicDBList
                list.add(value.value.toString)
                list
        }

    def queryEntityProperty(value: StatementValue): String =
        value match {
            case value: ToUpperCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toUpperCase function for queries.")
            case value: ToLowerCase =>
                throw new UnsupportedOperationException("Mongo storage doesn't support the toLowerCase function for queries.")
            case value: StatementEntitySourcePropertyValue[_] =>
                val name = value.propertyPathNames.onlyOne
                if (name == "id")
                    "_id"
                else
                    name
            case value: StatementEntitySourceValue[_] =>
                "_id"
            case other =>
                throw new UnsupportedOperationException("Mongo storage doesn't support joins.")
        }

    def query(operator: CompositeOperator): String =
        operator match {
            case operator: And =>
                "$and"
            case operator: Or =>
                "$or"
            case operator: IsGreaterOrEqualTo =>
                "$gte"
            case operator: IsGreaterThan =>
                "$gt"
            case operator: IsLessOrEqualTo =>
                "$lte"
            case operator: IsLessThan =>
                "$lt"
            case operator: Matcher =>
                "$regex"
            case operator: IsNotEqualTo =>
                "$ne"
            case operator: IsEqualTo =>
                throw new UnsupportedOperationException("Mongo doesn't have $eq operator yet (https://jira.mongodb.org/browse/SERVER-1367).")
        }

    override def migrateStorage(action: ModifyStorageAction): Unit =
        action match {
            case action: StorageCreateTable =>
                if (!action.ifNotExists || !mongoDB.collectionExists(action.tableName))
                    mongoDB.createCollection(action.tableName, new BasicDBObject)
            case action: StorageRenameTable =>
                coll(action.oldName).rename(action.newName)
            case action: StorageRemoveTable =>
                coll(action.name).drop
            case action: StorageAddColumn =>
            // Do nothing!
            case action: StorageRenameColumn =>
                val update = new BasicDBObject
                val updateInner = new BasicDBObject
                updateInner.put(action.oldName, action.column.name)
                update.put("$rename", updateInner)
                coll(action.tableName).update(new BasicDBObject, update)
            case action: StorageRemoveColumn =>
                val update = new BasicDBObject
                val updateInner = new BasicDBObject
                updateInner.put(action.name, 1)
                update.put("$unset", updateInner)
                coll(action.tableName).update(new BasicDBObject, update)
            case action: StorageAddIndex =>
                val obj = new BasicDBObject
                obj.put(action.columnName, 1)
                val options = new BasicDBObject
                if (action.unique)
                    options.put("unique", true)
                if (!action.ifNotExists || !collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).ensureIndex(obj, options)
            case action: StorageRemoveIndex =>
                val obj = new BasicDBObject
                obj.put(action.columnName, 1)
                if (!action.ifExists || collHasIndex(action.tableName, action.columnName))
                    coll(action.tableName).dropIndex(obj)
            case action: StorageAddReference =>
            // Do nothing!
            case action: StorageRemoveReference =>
            // Do nothing!
            case action: StorageCreateListTable =>
            // Do nothing!
            case action: StorageRemoveListTable =>
            // Do nothing!
        }

    private def collHasIndex(name: String, column: String) =
        coll(name).getIndexInfo().find(_.containsField(name)).nonEmpty

    private def collectionAndWhere(from: From, where: Where, entitiesReadFromCache: List[List[Entity]] = List()) = {
        val baseWhere = query(where.value)
        val mongoWhere =
            if (entitiesReadFromCache.nonEmpty) {
                val where = new BasicDBObject
                val andConditions = new BasicDBList
                andConditions.add(baseWhere)
                val fromCacheIds = new BasicDBList
                for (list <- entitiesReadFromCache)
                    fromCacheIds.add(list.head.id)
                val fromCacheIdsCondition = new BasicDBObject
                val fromCacheIdsNinCondition = new BasicDBObject
                fromCacheIdsNinCondition.put("$nin", fromCacheIds)
                fromCacheIdsCondition.put("_id", fromCacheIdsNinCondition)
                andConditions.add(fromCacheIdsCondition)
                where.put("$and", andConditions)
                where
            } else {
                baseWhere
            }
        val entitySource = from.entitySources.onlyOne("Mongo storage supports only simple queries (only one 'from' entity and without nested properties)")
        val mongoCollection = coll(entitySource.entityClass)
        (mongoCollection, mongoWhere)
    }

    private def limitQueryIfNecessary(queryInstance: Query[_], ret: DBCursor) =
        queryInstance match {
            case q: LimitedOrderedQuery[_] =>
                ret.limit(q.limit)
            case other =>
        }

    private def orderQueryIfNecessary(queryInstance: Query[_], ret: DBCursor) =
        queryInstance match {
            case q: OrderedQuery[_] =>
                val order = new BasicDBObject
                for (criteria <- q.orderByClause.get.criterias) {
                    val property = mongoStatementSelectValue(criteria.value)
                    val direction =
                        if (criteria.direction == orderByAscendingDirection)
                            1
                        else
                            -1
                    order.put(property, direction)
                }
                ret.sort(order)
            case other =>
        }

    private def transformResultToTheExpectedTypes(expectedTypes: List[StorageValue], selectValues: Seq[StatementSelectValue[_]], ret: DBCursor) = {
        try {
            val rows = ret.toArray
            (for (row <- rows) yield (for (i <- 0 until selectValues.size) yield {
                selectValues(i) match {
                    case value: SimpleValue[_] =>
                        expectedTypes(i)
                    case other =>
                        getValue(row, mongoStatementSelectValue(other), expectedTypes(i))
                }
            }).toList).toList
        } finally
            ret.close
    }

    private def querySelect(queryInstance: Query[_], selectValues: Seq[StatementSelectValue[_]]) = {
        val select = new BasicDBObject
        for (value <- selectValues)
            if (!value.isInstanceOf[SimpleValue[_]])
                select.put(mongoStatementSelectValue(value), 1)
        select
    }

}

object MongoStorageFactory extends StorageFactory {
    class MongoStorageFromFactory(properties: Map[String, String]) extends MongoStorage {
        override val host = properties("host")
        override val port = Integer.parseInt(properties("port"))
        override val db = properties("db")
        override val authentication =
            properties.get("user").map(user => (user, properties("password")))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new MongoStorageFromFactory(properties)
}
