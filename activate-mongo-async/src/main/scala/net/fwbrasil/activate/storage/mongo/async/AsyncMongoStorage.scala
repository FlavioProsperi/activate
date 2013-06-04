package net.fwbrasil.activate.storage.mongo.async

import language.implicitConversions
import net.fwbrasil.activate.storage.marshalling.MarshalStorage
import net.fwbrasil.activate.entity.EntityHelper.getEntityName
import scala.concurrent.duration._
import scala.concurrent.Await
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.OptimisticOfflineLocking.versionVarName
import reactivemongo.bson.BSONArray
import com.mongodb.BasicDBObject
import net.fwbrasil.activate.storage.mongo.mongoIdiom
import reactivemongo.bson._
import reactivemongo.api._
import reactivemongo.api.collections.default._
import reactivemongo.api.collections.default.BSONGenericHandlers._
import scala.concurrent.Future
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import scala.concurrent.ExecutionContext
import net.fwbrasil.activate.statement.mass.MassUpdateStatement
import net.fwbrasil.activate.statement.From
import net.fwbrasil.activate.statement.mass.MassDeleteStatement
import play.api.libs.iteratee.Enumerator
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import scala.concurrent.Awaitable
import reactivemongo.api.indexes.IndexType
import reactivemongo.api.indexes.Index
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery
import net.fwbrasil.activate.statement.SimpleValue
import java.util.Date
import scala.util.Success
import net.fwbrasil.activate.statement.query.LimitedOrderedQuery

trait AsyncMongoStorage extends MarshalStorage[DefaultDB] with DelayedInit {

    val executionContext = scala.concurrent.ExecutionContext.Implicits.global
    implicit val defaultTimeout = 9999 seconds

    val host: String
    val port: Int = 27017
    val db: String
    val authentication: Option[(String, String)] = None

    def directAccess =
        mongoDB

    private var mongoDB: DefaultDB = _

    override def delayedInit(body: => Unit) = {
        body
        val driver = new MongoDriver
        val conn = driver.connection(List(host + ":" + port))
        if (authentication.isDefined) {
            val (user, password) = authentication.get
            await(conn.authenticate(db, user, password))
        }
        mongoDB = conn.db(db)(executionContext)
    }

    def isMemoryStorage = false
    def isSchemaless = true
    def isTransactional = false
    def supportsQueryJoin = false
    override def supportsAsync = true

    override def store(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])]): Option[TransactionHandle] = {

        await(
            storeAsync(
                statements,
                insertList,
                updateList,
                deleteList)(executionContext))
        None
    }

    override def storeAsync(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, StorageValue])],
        updateList: List[(Entity, Map[String, StorageValue])],
        deleteList: List[(Entity, Map[String, StorageValue])])(implicit ecxt: ExecutionContext): Future[Unit] = {

        preVerifyStaleData(updateList ++ deleteList).flatMap { _ =>
            storeStatements(statements).flatMap { _ =>
                storeInserts(insertList).flatMap { _ =>
                    storeUpdates(updateList).flatMap { _ =>
                        storeDeletes(deleteList)
                    }
                }
            }
        }
    }

    override def query(
        query: Query[_],
        expectedTypes: List[StorageValue],
        entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] = {
        val (where, select) = mongoIdiom.toQuery(query, entitiesReadFromCache)
        val order = mongoIdiom.toQueryOrder(query)
        await(queryAsync(query, where, select, order, expectedTypes, entitiesReadFromCache)(executionContext))
    }

    override protected[activate] def queryAsync(query: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]])(implicit context: ExecutionContext): Future[List[List[StorageValue]]] = {
        Future(mongoIdiom.toQuery(query, entitiesReadFromCache)).flatMap { tuple =>
            val (where, select) = tuple
            val order = mongoIdiom.toQueryOrder(query)
            queryAsync(
                query,
                where,
                select,
                order,
                expectedTypes,
                entitiesReadFromCache)
        }
    }

    private def queryAsync(
        query: Query[_],
        where: Map[String, Any],
        select: Map[String, Any],
        order: Map[String, Any],
        expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]])(implicit context: ExecutionContext): Future[List[List[StorageValue]]] = {

        val options =
            query match {
                case query: LimitedOrderedQuery[_] =>
                    QueryOpts(query.offsetOption.getOrElse(0))
                case other =>
                    QueryOpts()
            }
        
        val ret = coll(query.from).find(dbObject(where), dbObject(select)).options(options)
        
        val sorted =
            if (order.nonEmpty)
                ret.sort(dbObject(order))
            else
                ret
        toQueryResult(query, sorted.cursor).map { result =>
            mongoIdiom.transformResultToTheExpectedTypes[BSONDocument](
                expectedTypes,
                query.select.values,
                result,
                rowToColumn = (doc, name) => storageValue(doc.get(name).orNull),
                fromDBList = obj => obj.asInstanceOf[List[Any]])
        }
    }

    override def migrateStorage(action: ModifyStorageAction): Unit = {
        implicit val cxt = executionContext
        action match {
            case action: StorageCreateTable =>
                coll(action.tableName)
            case action: StorageRenameTable =>
                await(coll(action.oldName).rename(action.newName))
            case action: StorageRemoveTable =>
                if (!action.ifExists || collectionsNames.contains(action.name))
                    await(coll(action.name).drop())
            case action: StorageAddColumn =>
            // Do nothing!
            case action: StorageRenameColumn =>
                val update = mongoIdiom.renameColumn(action.oldName, action.column.name)
                coll(action.tableName).update(BSONDocument(), dbObject(update))
            case action: StorageRemoveColumn =>
                val update = mongoIdiom.removeColumn(action.name)
                coll(action.tableName).update(BSONDocument(), dbObject(update))
            case action: StorageAddIndex =>
                val manager = coll(action.tableName).indexesManager
                val future = manager.ensure(
                    Index(Seq((action.columnName, IndexType.Ascending)), unique = action.unique))
                await(future)
            case action: StorageRemoveIndex =>
                val manager = coll(action.tableName).indexesManager
                manager.delete(Index(Seq((action.columnName, IndexType.Ascending))))
            case action: StorageAddReference =>
            // Do nothing!
            case action: StorageRemoveReference =>
            // Do nothing!
            case action: StorageCreateListTable =>
            // Do nothing!
            case action: StorageRemoveListTable =>
            // Do nothing!
        }
    }

    private def collectionsNames =
        await(mongoDB.collectionNames(executionContext))

    private def toQueryResult(query: Query[_], cursor: Cursor[BSONDocument]) =
        try query match {
            case q: LimitedOrderedQuery[_] =>
                cursor.toList(q.limit)(executionContext)
            case other =>
                cursor.toList()(executionContext)
        } finally {
            cursor.close
        }

    private def storeStatements(statements: List[MassModificationStatement])(implicit ctx: ExecutionContext) =
        statements.foldLeft(Future()) { (future, statement) =>
            future.flatMap { _ =>
                val where = dbObject(mongoIdiom.toQueryWhere(statement.where))
                val coll = this.coll(statement.from)
                (statement match {
                    case update: MassUpdateStatement =>
                        val mongoUpdate = dbObject(mongoIdiom.toQueryUpdate(update))
                        coll.update(where, mongoUpdate, upsert = false, multi = true)
                    case delete: MassDeleteStatement =>
                        coll.remove(where)
                }).map { _ => }
            }
        }

    private def storeInserts(insertList: List[(Entity, Map[String, StorageValue])])(implicit ctx: ExecutionContext) =
        Future(mongoIdiom.toInsertMap(insertList)).flatMap { insertMap =>
            insertMap.keys.toList.foldLeft(Future()) { (future, entityClass) =>
                future.flatMap { _ =>
                    val inserts = insertMap(entityClass).toList.map(dbObject(_))
                    //                    inserts.foldLeft(Future()) { (future, insert) =>
                    //                    	future.flatMap { _ =>
                    //                    	    coll(entityClass).insert(insert).map { _ =>}
                    //                    	}
                    //                    }
                    val enumerator = Enumerator(inserts: _*)
                    coll(entityClass).bulkInsert(enumerator).map { _ => }
                }
            }
        }

    private def storeUpdates(updateList: List[(Entity, Map[String, StorageValue])])(implicit ctx: ExecutionContext) = {
        updateList.foldLeft(Future()) { (future, tuple) =>
            future.flatMap { _ =>
                val (entity, properties) = tuple
                val (query, set) = mongoIdiom.toUpdate(entity, properties)
                coll(entity).update(dbObject(query), dbObject(set)).map {
                    result =>
                        if (result.n != 1)
                            staleDataException(Set(entity.id))
                }
            }
        }
    }

    private def storeDeletes(deleteList: List[(Entity, Map[String, StorageValue])])(implicit ctx: ExecutionContext) = {
        deleteList.foldLeft(Future()) { (future, tuple) =>
            future.flatMap { _ =>
                val (entity, properties) = tuple
                val query = mongoIdiom.toDelete(entity, properties)
                coll(entity).remove(dbObject(query)).map {
                    result =>
                        if (result.n != 1)
                            staleDataException(Set(entity.id))
                }
            }
        }
    }

    private def dbValue(obj: Any): BSONValue =
        obj match {
            case null =>
                BSONNull
            case map: Map[String, Any] =>
                dbObject(map)
            case list: List[Any] =>
                dbList(list)
            case v: Array[Byte] =>
                BSONBinary(v, Subtype.GenericBinarySubtype)
            case v: Date =>
                BSONDateTime(v.getTime)
            case v: Int =>
                BSONInteger(v)
            case v: Long =>
                BSONLong(v)
            case v: Double =>
                BSONDouble(v)
            case v: String =>
                BSONString(v)
            case v: Boolean =>
                BSONBoolean(v)
        }

    private def storageValue(obj: BSONValue): Any =
        obj match {
            case BSONNull =>
                null
            case BSONArray(v) =>
                v.collect { case Success(res) => storageValue(res) }.toList
            case BSONBinary(v, t) =>
                val array = new Array[Byte](v.size)
                v.readBytes(array)
                array
            case BSONDateTime(v) =>
                new Date(v)
            case BSONInteger(v) =>
                v
            case BSONLong(v) =>
                v
            case BSONDouble(v) =>
                v
            case BSONString(v) =>
                v
            case BSONBoolean(v) =>
                v
            case other =>
                null
        }

    private implicit def dbList(list: List[_]) =
        BSONArray(list.map(dbValue(_)))

    private implicit def dbObject(map: Map[String, Any]) =
        BSONDocument(map.map(tuple => tuple._1 -> dbValue(tuple._2)))

    private def preVerifyStaleData(
        data: List[(Entity, Map[String, StorageValue])])(implicit ctx: ExecutionContext) =
        Future {
            mongoIdiom.findStaleDataQueries(data)
        }.flatMap { queries =>
            queries.foldLeft(Future(List[BSONDocument]())) { (future, query) =>
                future.flatMap { list =>
                    val (entity, where, select) = query
                    val cursor = coll(entity).find(dbObject(where), dbObject(select)).cursor[BSONDocument]
                    try cursor.toList.map { _ ++ list }
                    finally cursor.close
                }
            }
        }.map { stale =>
            if (stale.nonEmpty)
                staleDataException(stale.map(_.getAs[String]("_id")).flatten.toSet)
        }

    private[this] def coll(from: From): BSONCollection =
        coll(mongoIdiom.collectionClass(from))

    private[this] def coll(entity: Entity): BSONCollection =
        coll(entity.getClass)

    private[this] def coll(entityClass: Class[_]): BSONCollection =
        coll(getEntityName(entityClass))

    private def await[T](a: Awaitable[T]) =
        Await.result(a, defaultTimeout)

    private def coll(entityName: String) =
        mongoDB.collection[BSONCollection](entityName)

}