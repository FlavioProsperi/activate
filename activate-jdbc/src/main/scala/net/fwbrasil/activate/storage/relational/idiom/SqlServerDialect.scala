package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import net.fwbrasil.activate.storage.marshalling.DoubleStorageValue
import net.fwbrasil.activate.storage.marshalling.IntStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveTable
import net.fwbrasil.activate.storage.marshalling.BigDecimalStorageValue
import net.fwbrasil.activate.storage.marshalling.LongStorageValue
import net.fwbrasil.activate.storage.marshalling.StringStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageValue
import net.fwbrasil.activate.storage.marshalling.DateStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageAddColumn
import net.fwbrasil.activate.storage.marshalling.ModifyStorageAction
import net.fwbrasil.activate.storage.marshalling.FloatStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageAddIndex
import net.fwbrasil.activate.storage.marshalling.StorageAddReference
import net.fwbrasil.activate.storage.marshalling.ReferenceStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRenameColumn
import net.fwbrasil.activate.storage.marshalling.StorageCreateTable
import net.fwbrasil.activate.storage.marshalling.StorageRemoveReference
import net.fwbrasil.activate.storage.marshalling.StorageRemoveColumn
import net.fwbrasil.activate.storage.marshalling.ByteArrayStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveIndex
import net.fwbrasil.activate.storage.marshalling.ListStorageValue
import net.fwbrasil.activate.storage.marshalling.StorageRemoveListTable
import net.fwbrasil.activate.storage.marshalling.StorageCreateListTable
import net.fwbrasil.activate.storage.marshalling.StorageModifyColumnType
import java.sql.PreparedStatement
import java.util.Date
import java.sql.Types

object sqlServerDialect extends SqlIdiom {
    
    override def supportsLimitedQueries = false
    override def supportsRegex = false

    def toSqlDmlRegexp(value: String, regex: String) =
        value + " LIKE " + regex

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.TABLES " +
            " WHERE TABLE_SCHEMA = SCHEMA_NAME() " +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.COLUMNS " +
            " WHERE TABLE_SCHEMA = SCHEMA_NAME() " +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'" +
            "   AND COLUMN_NAME = '" + columnName.toUpperCase + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1)" +
            " FROM SYS.INDEXES I, " +
            "      SYS.TABLES T, " +
            "      SYS.SCHEMAS S " +
            "WHERE T.OBJECT_ID = I.OBJECT_ID " +
            "  AND T.SCHEMA_ID = S.SCHEMA_ID " +
            "  AND S.NAME = SCHEMA_NAME() " +
            s"  AND T.NAME = '${tableName.toUpperCase}' " +
            s"  AND I.NAME = '${indexName.toUpperCase}' "

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS " +
            " WHERE TABLE_SCHEMA = SCHEMA_NAME() " +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'" +
            "   AND CONSTRAINT_NAME = '" + constraintName.toUpperCase + "'"

    override def escape(string: String) =
        "\"" + string + "\""

    override def toSqlDdl(action: ModifyStorageAction): String = {
        action match {
            case StorageRemoveListTable(ownerTableName, listName, ifNotExists) =>
                "DROP TABLE " + escape(ownerTableName + listName.capitalize)
            case StorageCreateListTable(ownerTableName, listName, valueColumn, orderColumn, ifNotExists) =>
                "CREATE TABLE " + escape(ownerTableName + listName.capitalize) + "(\n" +
                    "	" + escape("owner") + " " + toSqlDdl(ReferenceStorageValue(None)) + " REFERENCES " + escape(ownerTableName) + "(ID),\n" +
                    toSqlDdl(valueColumn) + ", " + toSqlDdl(orderColumn) +
                    ")"
            case StorageCreateTable(tableName, columns, ifNotExists) =>
                "CREATE TABLE " + escape(tableName) + "(\n" +
                    "	ID " + toSqlDdl(ReferenceStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
                    columns.map(toSqlDdl).mkString(", \n") +
                    ")"
            case StorageRenameTable(oldName, newName, ifExists) =>
                "ALTER TABLE " + escape(oldName) + " RENAME TO " + escape(newName)
            case StorageRemoveTable(name, ifExists, isCascade) =>
                "DROP TABLE " + escape(name)
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(oldName) + " RENAME TO " + escape(column.name)
            case StorageModifyColumnType(tableName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(column.name) + " " + columnType(column)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
            case StorageAddIndex(tableName, columnName, indexName, ifNotExists, unique) =>
                "CREATE " + (if (unique) "UNIQUE " else "") + "INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
            case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
                "DROP INDEX " + escape(name) + " ON " + escape(tableName)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
        }
    }
    
    override def getValue(resultSet: ActivateResultSet, i: Int, storageValue: StorageValue) = {
        storageValue match {
            case value: DateStorageValue =>
                DateStorageValue(resultSet.getLong(i).map((t: Long) => new Date(t)))
            case other =>
                super.getValue(resultSet, i, storageValue)
        }
    }

    override def setValue(ps: PreparedStatement, i: Int, storageValue: StorageValue): Unit = {
        storageValue match {
            case value: DateStorageValue =>
                setValue(ps, (v: Date) => ps.setLong(i, v.getTime), i, value.value, Types.BIGINT)
            case other =>
                super.setValue(ps, i, storageValue)
        }
    }

    def concat(strings: String*) =
        strings.map(s => s" CAST($s AS VARCHAR(1000))").mkString(" + ") 

    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "INTEGER"
            case value: LongStorageValue =>
                "BIGINT"
            case value: BooleanStorageValue =>
                "BIT"
            case value: StringStorageValue =>
                "VARCHAR(1000)"
            case value: FloatStorageValue =>
                "REAL"
            case value: DateStorageValue =>
                "BIGINT"
            case value: DoubleStorageValue =>
                "DOUBLE PRECISION"
            case value: BigDecimalStorageValue =>
                "DECIMAL"
            case value: ByteArrayStorageValue =>
                "VARBINARY(8000)"
            case value: ListStorageValue =>
                "INTEGER"
            case value: ReferenceStorageValue =>
                "VARCHAR(45)"
        }
}

