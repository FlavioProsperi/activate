package net.fwbrasil.activate.storage.relational

import language.existentials
import net.fwbrasil.scala.UnsafeLazy._
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import javax.naming.InitialContext
import javax.sql.DataSource
import net.fwbrasil.activate.statement.query.Query
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.Storage
import net.fwbrasil.activate.storage.StorageFactory
import net.fwbrasil.activate.util.Logging
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.storage.relational.idiom.SqlIdiom
import java.sql.BatchUpdateException
import com.jolbox.bonecp.BoneCP
import com.jolbox.bonecp.BoneCPConfig
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.TransactionHandle
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.ActivateConcurrentTransactionException
import java.sql.PreparedStatement

case class JdbcStatementException(statement: JdbcStatement, exception: Exception, nextException: Exception)
    extends Exception("Statement exception: " + statement + ". Next exception: " + Option(nextException).map(_.getMessage), exception)

trait JdbcRelationalStorage extends RelationalStorage[Connection] with Logging {

    val dialect: SqlIdiom
    val batchLimit = 1000
    private val preparedStatementCache = new PreparedStatementCache

    protected def getConnection: Connection

    override def reinitialize = {
        preparedStatementCache.clear
        super.reinitialize
    }

    override protected[activate] def prepareDatabase =
        dialect.prepareDatabase(this)

    private def getConnectionWithAutoCommit = {
        val con = getConnection
        con.setAutoCommit(true)
        con
    }

    private def getConnectionWithoutAutoCommit = {
        val con = getConnection
        con.setAutoCommit(false)
        con
    }

    def directAccess =
        getConnectionWithoutAutoCommit

    def isMemoryStorage = false
    def isSchemaless = false
    def isTransactional = true
    def supportsQueryJoin = true

    override protected[activate] def executeStatements(
            storageStatements: List[StorageStatement]): Option[TransactionHandle] = {
        val sqlStatements =
            storageStatements.map(dialect.toSqlStatement).flatten
        val statements =
            BatchSqlStatement.group(sqlStatements, batchLimit)
        Some(executeWithTransactionAndReturnHandle {
            connection =>
                for (statement <- statements)
                    execute(statement, connection)
        })
    }

    private protected[activate] def satisfyRestriction(jdbcStatement: JdbcStatement) =
        jdbcStatement.restrictionQuery.map(tuple => {
            executeWithTransaction(autoCommit = true) {
                connection =>
                    val (query, expected) = tuple
                    val stmt = connection.prepareStatement(query)
                    val result =
                        try {
                            val resultSet = stmt.executeQuery
                            try {
                                resultSet.next
                                resultSet.getInt(1)
                            } finally
                                resultSet.close
                        } finally
                            stmt.close
                    result == expected
            }
        }).getOrElse(true)

    def execute(jdbcStatement: JdbcStatement, connection: Connection) =
        try
            if (satisfyRestriction(jdbcStatement)) {
                val stmt = acquirePreparedStatement(jdbcStatement, connection, true)
                try {
                    val result = jdbcStatement match {
                        case normal: SqlStatement =>
                            Array(stmt.executeUpdate)
                        case batch: BatchSqlStatement =>
                            stmt.executeBatch
                    }
                    verifyStaleData(jdbcStatement, result)

                } finally
                    releasePreparedStatement(jdbcStatement, connection, stmt)
            }
        catch {
            case e: BatchUpdateException =>
                throw JdbcStatementException(jdbcStatement, e, e.getNextException)
            case e: ActivateConcurrentTransactionException =>
                throw e
            case other: Exception =>
                throw JdbcStatementException(jdbcStatement, other, other)
        }

    protected[activate] def query(queryInstance: Query[_], expectedTypes: List[StorageValue], entitiesReadFromCache: List[List[Entity]]): List[List[StorageValue]] =
        executeQuery(dialect.toSqlDml(QueryStorageStatement(queryInstance, entitiesReadFromCache)), expectedTypes)

    protected[activate] def executeQuery(sqlStatement: SqlStatement, expectedTypes: List[StorageValue]): List[List[StorageValue]] = {
        executeWithTransaction(autoCommit = true) {
            connection =>
                val stmt = acquirePreparedStatement(sqlStatement, connection, false)
                try {
                    val resultSet = stmt.executeQuery
                    try {
                        var result = List[List[StorageValue]]()
                        while (resultSet.next) {
                            var i = 0
                            result ::=
                                (for (expectedType <- expectedTypes) yield {
                                    i += 1
                                    dialect.getValue(resultSet, i, expectedType, connection)
                                })
                        }
                        result
                    } finally
                        resultSet.close
                } finally
                    releasePreparedStatement(sqlStatement, connection, stmt)
        }
    }

    private def releasePreparedStatement(jdbcStatement: JdbcStatement, connection: Connection, ps: PreparedStatement) =
        preparedStatementCache.release(connection, jdbcStatement.indexedStatement, ps)

    protected[activate] def acquirePreparedStatement(jdbcStatement: JdbcStatement, connection: Connection, readOnly: Boolean) = {
        val statement = jdbcStatement.indexedStatement
        val valuesList = jdbcStatement.valuesList
        val ps = preparedStatementCache.acquireFor(connection, statement, readOnly)
        try {
            for (binds <- valuesList) {
                var i = 1
                for (bindValue <- binds) {
                    dialect.setValue(ps, i, bindValue)
                    i += 1
                }
                if (readOnly && jdbcStatement.isInstanceOf[BatchSqlStatement])
                    ps.addBatch
            }
        } catch {
            case e: Throwable =>
                ps.close
                throw e
        }
        ps
    }

    def executeWithTransactionAndReturnHandle[R](f: (Connection) => R) = {
        val connection = getConnectionWithoutAutoCommit
        try {
            f(connection)
            new TransactionHandle(
                commitBlock = () => connection.commit,
                rollbackBlock = () => connection.rollback,
                finallyBlock = () => connection.close)
        } catch {
            case e: Throwable =>
                try connection.rollback
                finally connection.close
                throw e
        }
    }

    def executeWithTransaction[R](f: (Connection) => R): R =
        executeWithTransaction(false)(f)

    def executeWithTransaction[R](autoCommit: Boolean)(f: (Connection) => R) = {
        val connection =
            if (autoCommit)
                getConnectionWithAutoCommit
            else
                getConnectionWithoutAutoCommit
        try {
            val res = f(connection)
            if (!autoCommit)
                connection.commit
            res
        } catch {
            case e: Throwable =>
                if (!autoCommit)
                    connection.rollback
                throw e
        } finally
            connection.close
    }

    private def verifyStaleData(jdbcStatement: JdbcStatement, result: Array[Int]): Unit = {
        val expectedResult = jdbcStatement.expectedNumbersOfAffectedRowsOption
        require(result.size == expectedResult.size)
        val invalidIds =
            (for (i <- 0 until result.size) yield {
                expectedResult(i).filter(_ != result(i)).map(_ => i)
            }).flatten
                .flatMap(jdbcStatement.bindsList(_).get("id"))
                .collect {
                    case StringStorageValue(Some(value: String)) =>
                        value
                    case ReferenceStorageValue(Some(value: String)) =>
                        value
                }
        if (invalidIds.nonEmpty)
            staleDataException(invalidIds.toSet)
    }

}

trait SimpleJdbcRelationalStorage extends JdbcRelationalStorage with DelayedInit {

    val jdbcDriver: String
    val url: String
    val user: String
    val password: String

    override def delayedInit(body: => Unit) {
        body
        Class.forName(jdbcDriver)
    }

    override def getConnection =
        DriverManager.getConnection(url, user, password)
}

trait PooledJdbcRelationalStorage extends JdbcRelationalStorage with DelayedInit {

    val jdbcDriver: String
    val url: String
    val user: String
    val password: String

    private var _connectionPool: BoneCP = _

    override def delayedInit(body: => Unit) = {
        body
        initConnectionPool
    }

    def connectionPool =
        _connectionPool

    override def reinitialize = {
        _connectionPool.close
        while (_connectionPool.getTotalLeased != 0)
            Thread.sleep(10)
        initConnectionPool
        super.reinitialize
    }

    override def getConnection =
        _connectionPool.getConnection

    private def initConnectionPool = {
        Class.forName(jdbcDriver)
        val config = new BoneCPConfig
        config.setJdbcUrl(url)
        config.setUsername(user)
        config.setPassword(password)
        config.setLazyInit(true)
        config.setDisableConnectionTracking(true)
        _connectionPool = new BoneCP(config)
    }

}

trait DataSourceJdbcRelationalStorage extends JdbcRelationalStorage {

    val dataSourceName: String
    val initialContext = new InitialContext()
    val dataSource = initialContext.lookup(dataSourceName).asInstanceOf[DataSource]

    override def getConnection =
        dataSource.getConnection
}

object PooledJdbcRelationalStorageFactory extends StorageFactory {
    class PooledJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends PooledJdbcRelationalStorage {
        val jdbcDriver = properties("jdbcDriver")
        val url = properties("url")
        val user = properties("user")
        val password = properties("password")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new PooledJdbcRelationalStorageFromFactory(properties)
}

object SimpleJdbcRelationalStorageFactory extends StorageFactory {
    class SimpleJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends SimpleJdbcRelationalStorage {
        val jdbcDriver = properties("jdbcDriver")
        val url = properties("url")
        val user = properties("user")
        val password = properties("password")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new SimpleJdbcRelationalStorageFromFactory(properties)
}

object DataSourceJdbcRelationalStorageFactory extends StorageFactory {
    class DataSourceJdbcRelationalStorageFromFactory(val properties: Map[String, String]) extends DataSourceJdbcRelationalStorage {
        val dataSourceName = properties("dataSourceName")
        val dialect = SqlIdiom.dialect(properties("dialect"))
    }
    override def buildStorage(properties: Map[String, String])(implicit context: ActivateContext): Storage[_] =
        new DataSourceJdbcRelationalStorageFromFactory(properties)
}