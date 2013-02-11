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

object hsqldbDialect extends SqlIdiom {

    def toSqlDmlRegexp(value: String, regex: String) =
        "REGEXP_MATCHES(" + value + ", " + regex + ")"

    override def findTableStatement(tableName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.TABLES " +
            " WHERE TABLE_SCHEMA = CURRENT_SCHEMA" +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'"

    override def findTableColumnStatement(tableName: String, columnName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.COLUMNS " +
            " WHERE TABLE_SCHEMA = CURRENT_SCHEMA " +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'" +
            "   AND COLUMN_NAME = '" + columnName.toUpperCase + "'"

    override def findIndexStatement(tableName: String, indexName: String) =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.SYSTEM_INDEXINFO " +
            " WHERE TABLE_SCHEM = CURRENT_SCHEMA " +
            "   AND TABLE_NAME = '" + tableName.toUpperCase + "'" +
            "   AND INDEX_NAME = '" + indexName.toUpperCase + "'"

    override def findConstraintStatement(tableName: String, constraintName: String): String =
        "SELECT COUNT(1) " +
            "  FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS " +
            " WHERE CONSTRAINT_SCHEMA = CURRENT_SCHEMA " +
            "   AND CONSTRAINT_NAME = '" + constraintName.toUpperCase + "'"

    override def escape(string: String) =
        "\"" + string.toUpperCase + "\""

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
                "DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
            case StorageAddColumn(tableName, column, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
            case StorageRenameColumn(tableName, oldName, column, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " ALTER COLUMN " + escape(oldName) + " RENAME TO " + escape(column.name)
            case StorageRemoveColumn(tableName, name, ifExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
            case StorageAddIndex(tableName, columnName, indexName, ifNotExists) =>
                "CREATE INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
            case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
                "DROP INDEX " + escape(name)
            case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
            case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
                "ALTER TABLE " + escape(tableName) + " DROP CONSTRAINT " + escape(constraintName)
        }
    }

    def concat(strings: String*) =
        "CONCAT(" + strings.mkString(", ") + ")"

    override def toSqlDdl(storageValue: StorageValue): String =
        storageValue match {
            case value: IntStorageValue =>
                "INTEGER"
            case value: LongStorageValue =>
                "BIGINT"
            case value: BooleanStorageValue =>
                "BOOLEAN"
            case value: StringStorageValue =>
                "VARCHAR(1000)"
            case value: FloatStorageValue =>
                "DOUBLE"
            case value: DateStorageValue =>
                "TIMESTAMP"
            case value: DoubleStorageValue =>
                "DOUBLE"
            case value: BigDecimalStorageValue =>
                "DECIMAL"
            case value: ListStorageValue =>
                "INTEGER"
            case value: ByteArrayStorageValue =>
                "BLOB"
            case value: ReferenceStorageValue =>
                "VARCHAR(45)"
        }
}

