package net.fwbrasil.activate.storage

import net.fwbrasil.activate.entity.Var
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.entity.EntityValue
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.migration.StorageAction
import net.fwbrasil.activate.util.Reflection
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.ActivateProperties
import net.fwbrasil.activate.statement.Statement
import net.fwbrasil.activate.statement.mass.MassModificationStatement
import scala.annotation.implicitNotFound
import net.fwbrasil.activate.ActivateConcurrentTransactionException
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

class TransactionHandle(
        private val commitBlock: () => Unit,
        private val rollbackBlock: () => Unit,
        private val finallyBlock: () => Unit) {
    def commit =
        try commitBlock()
        finally finallyBlock()
    def rollback =
        try rollbackBlock()
        finally finallyBlock()
}

trait Storage[T] {

    protected[activate] def toStorage(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])]): Option[TransactionHandle]
    
    protected[activate] def toStorageAsync(
        statements: List[MassModificationStatement],
        insertList: List[(Entity, Map[String, EntityValue[Any]])],
        updateList: List[(Entity, Map[String, EntityValue[Any]])],
        deleteList: List[(Entity, Map[String, EntityValue[Any]])])(implicit ecxt: ExecutionContext): Future[Unit] =
            throw new UnsupportedOperationException("The storage does not support async queries.")

    protected[activate] def fromStorage(query: Query[_], entitiesReadFromCache: List[List[Entity]]): List[List[EntityValue[_]]]
    
    protected[activate] def fromStorageAsync(query: Query[_], entitiesReadFromCache: List[List[Entity]])(implicit ecxt: ExecutionContext): Future[List[List[EntityValue[_]]]] =
        throw new UnsupportedOperationException("The storage does not support async queries.")
    
    def directAccess: T

    def isMemoryStorage: Boolean
    def isSchemaless: Boolean
    def isTransactional: Boolean
    def supportsQueryJoin: Boolean
    def supportsAsync = false

    protected[activate] def reinitialize = {}
    protected[activate] def migrate(action: StorageAction): Unit
    protected[activate] def prepareDatabase = {}
    protected def staleDataException(entityIds: Set[String]) =
        throw new ActivateConcurrentTransactionException(entityIds, List())

}

trait StorageFactory {
    def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_]
}

object StorageFactory {
    @implicitNotFound("ActivateContext implicit not found. Please import yourContext._")
    def fromSystemProperties(name: String)(implicit context: ActivateContext) = {
        import scala.collection.JavaConversions._
        val properties =
            new ActivateProperties(Option(context.properties), "storage")
        val factoryClassName =
            properties.getProperty(name, "factory")
        val storageFactory =
            Reflection.getCompanionObject[StorageFactory](Class.forName(factoryClassName)).get
        storageFactory.buildStorage(properties.childProperties(name))
    }
}
