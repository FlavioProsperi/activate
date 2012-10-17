package net.fwbrasil.activate.storage.relational.idiom

import net.fwbrasil.activate.storage.marshalling.BooleanStorageValue
import java.sql.ResultSet
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
import java.sql.PreparedStatement
import java.util.Date
import java.sql.Types
import net.fwbrasil.activate.storage.marshalling.ListStorageValue

object mySqlDialect extends SqlIdiom {

	override def getValue(resultSet: ResultSet, i: Int, storageValue: StorageValue) = {
		storageValue match {
			case value: DateStorageValue =>
				DateStorageValue(getValue(resultSet, resultSet.getLong(i)).map((t: Long) => new Date(t)))
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

	def toSqlDmlRegexp(value: String, regex: String) =
		value + " REGEXP " + regex

	override def findTableStatement(tableName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.TABLES " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'"

	override def findTableColumnStatement(tableName: String, columnName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.COLUMNS " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND COLUMN_NAME = '" + columnName + "'"

	override def findIndexStatement(tableName: String, indexName: String) =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.STATISTICS " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND INDEX_NAME = '" + indexName + "'"

	override def findConstraintStatement(tableName: String, constraintName: String): String =
		"SELECT COUNT(1) " +
			"  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE " +
			" WHERE TABLE_SCHEMA = (SELECT DATABASE()) " +
			"   AND TABLE_NAME = '" + tableName + "'" +
			"   AND CONSTRAINT_NAME = '" + constraintName + "'"

	override def escape(string: String) =
		"`" + string + "`"

	override def toSqlDdl(action: ModifyStorageAction): String = {
		action match {
			case StorageCreateTable(tableName, columns, ifNotExists) =>
				"CREATE TABLE " + escape(tableName) + "(\n" +
					"	ID " + toSqlDdl(ReferenceStorageValue(None)) + " PRIMARY KEY" + (if (columns.nonEmpty) ",\n" else "") +
					columns.map(toSqlDdl).mkString(", \n") +
					")"
			case StorageRenameTable(oldName, newName, ifExists) =>
				"RENAME TABLE " + escape(oldName) + " TO " + escape(newName)
			case StorageRemoveTable(name, ifExists, isCascade) =>
				"DROP TABLE " + escape(name) + (if (isCascade) " CASCADE" else "")
			case StorageAddColumn(tableName, column, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD " + toSqlDdl(column)
			case StorageRenameColumn(tableName, oldName, column, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " CHANGE " + escape(oldName) + " " + toSqlDdl(column)
			case StorageRemoveColumn(tableName, name, ifExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP COLUMN " + escape(name)
			case StorageAddIndex(tableName, columnName, indexName, ifNotExists) =>
				"CREATE INDEX " + escape(indexName) + " ON " + escape(tableName) + " (" + escape(columnName) + ")"
			case StorageRemoveIndex(tableName, columnName, name, ifExists) =>
				"DROP INDEX " + escape(name) + " ON " + escape(tableName)
			case StorageAddReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " ADD CONSTRAINT " + escape(constraintName) + " FOREIGN KEY (" + escape(columnName) + ") REFERENCES " + escape(referencedTable) + "(id)"
			case StorageRemoveReference(tableName, columnName, referencedTable, constraintName, ifNotExists) =>
				"ALTER TABLE " + escape(tableName) + " DROP FOREIGN KEY " + escape(constraintName)
		}
	}

	override def toSqlDdl(storageValue: StorageValue): String =
		storageValue match {
			case value: IntStorageValue =>
				"INTEGER"
			case value: LongStorageValue =>
				"BIGINT"
			case value: BooleanStorageValue =>
				"BOOLEAN"
			case value: StringStorageValue =>
				"VARCHAR(200)"
			case value: FloatStorageValue =>
				"DOUBLE"
			case value: DateStorageValue =>
				"LONG"
			case value: DoubleStorageValue =>
				"DOUBLE"
			case value: BigDecimalStorageValue =>
				"DECIMAL"
			case value: ListStorageValue =>
				"BLOB"
			case value: ByteArrayStorageValue =>
				"BLOB"
			case value: ReferenceStorageValue =>
				"VARCHAR(45)"
		}
}

