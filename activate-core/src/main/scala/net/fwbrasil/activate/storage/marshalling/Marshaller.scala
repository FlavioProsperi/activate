package net.fwbrasil.activate.storage.marshalling

import net.fwbrasil.activate.entity._
import net.fwbrasil.activate.query._
import java.util.{ Date, Calendar }
import net.fwbrasil.activate.util.ManifestUtil.manifestClass
import net.fwbrasil.activate.util.Reflection.newInstance
import net.fwbrasil.activate.util.Reflection.get
import net.fwbrasil.activate.util.Reflection.getObject
import net.fwbrasil.activate.util.Reflection.materializeJodaInstant
import org.joda.time.base.AbstractInstant

object Marshaller {

	def marshalling(value: QuerySelectValue[_]): StorageValue =
		value match {
			case value: QueryEntityValue[_] =>
				marshalling(value)
			case value: SimpleValue[_] =>
				marshalling(value)
		}

	def marshalling[V](value: QueryEntityValue[V]): StorageValue =
		value match {
			case value: QueryEntityInstanceValue[V] =>
				marshalling(value)
			case value: QueryEntitySourceValue[V] =>
				marshalling(value)
		}

	def marshalling[V <: Entity: Manifest](value: QueryEntityInstanceValue[V]): StorageValue =
		marshalling(EntityInstanceEntityValue[V](Option(value.entity)))

	def marshalling[V](value: QueryEntitySourceValue[V]): StorageValue =
		value match {
			case value: QueryEntitySourcePropertyValue[V] =>
				marshalling(value)
			case value: QueryEntitySourceValue[V] =>
				marshalling(EntityInstanceEntityValue(None)(manifestClass(value.entitySource.entityClass)))
		}

	def marshalling[P](value: QueryEntitySourcePropertyValue[P]): StorageValue =
		marshalling(value.lastVar.asInstanceOf[QueryMocks.FakeVarToQuery[_]].entityValueMock)

	def marshalling(value: SimpleValue[_]): StorageValue =
		marshalling(value.entityValue)

	def unmarshalling(storageValue: StorageValue): EntityValue[_] =
		(storageValue, storageValue.entityValue) match {
			case (storageValue: IntStorageValue, entityValue: IntEntityValue) =>
				IntEntityValue(storageValue.value)
			case (storageValue: BooleanStorageValue, entityValue: BooleanEntityValue) =>
				BooleanEntityValue(storageValue.value)
			case (storageValue: StringStorageValue, entityValue: CharEntityValue) =>
				CharEntityValue(storageValue.value.map(_.charAt(0)))
			case (storageValue: StringStorageValue, entityValue: StringEntityValue) =>
				StringEntityValue(storageValue.value)
			case (storageValue: FloatStorageValue, entityValue: FloatEntityValue) =>
				FloatEntityValue(storageValue.value)
			case (storageValue: DoubleStorageValue, entityValue: DoubleEntityValue) =>
				DoubleEntityValue(storageValue.value)
			case (storageValue: BigDecimalStorageValue, entityValue: BigDecimalEntityValue) =>
				BigDecimalEntityValue(storageValue.value)
			case (storageValue: DateStorageValue, entityValue: DateEntityValue) =>
				DateEntityValue(storageValue.value)
			case (storageValue: DateStorageValue, entityValue: JodaInstantEntityValue[_]) =>
				JodaInstantEntityValue(storageValue.value.map((date: Date) => materializeJodaInstant(entityValue.instantClass, date)))
			case (storageValue: DateStorageValue, entityValue: CalendarEntityValue) =>
				CalendarEntityValue(storageValue.value.map((v: Date) => {
					val calendar = Calendar.getInstance
					calendar.setTime(v)
					calendar
				}))
			case (storageValue: ByteArrayStorageValue, entityValue: ByteArrayEntityValue) =>
				ByteArrayEntityValue(storageValue.value)
			case (storageValue: ReferenceStorageValue, entityValue: EntityInstanceEntityValue[_]) =>
				EntityInstanceReferenceValue(storageValue.value)(entityValue.entityManifest)
			case (stringValue: StringStorageValue, enumerationValue: EnumerationEntityValue[_]) => {
				val value = if (stringValue.value.isDefined) {
					val enumerationValueClass = enumerationValue.enumerationClass
					val enumerationClass = enumerationValueClass.getEnclosingClass
					val enumerationObjectClass = Class.forName(enumerationClass.getName + "$")
					val obj = getObject[Enumeration](enumerationObjectClass)
					Option(obj.withName(stringValue.value.get))
				} else None
				EnumerationEntityValue(None)
			}
			case other =>
				throw new IllegalStateException("Invalid storage value.")
		}

	def marshalling(implicit entityValue: EntityValue[_]): StorageValue =
		(entityValue match {
			case value: IntEntityValue =>
				IntStorageValue(value.value)
			case value: BooleanEntityValue =>
				BooleanStorageValue(value.value)
			case value: CharEntityValue =>
				StringStorageValue(value.value.map(_.toString))
			case value: StringEntityValue =>
				StringStorageValue(value.value)
			case value: FloatEntityValue =>
				FloatStorageValue(value.value)
			case value: DoubleEntityValue =>
				DoubleStorageValue(value.value)
			case value: BigDecimalEntityValue =>
				BigDecimalStorageValue(value.value)
			case value: DateEntityValue =>
				DateStorageValue(value.value)
			case value: JodaInstantEntityValue[_] =>
				DateStorageValue(value.value.map(_.toDate))
			case value: CalendarEntityValue =>
				DateStorageValue(value.value.map(_.getTime))
			case value: ByteArrayEntityValue =>
				ByteArrayStorageValue(value.value)
			case value: EntityInstanceEntityValue[Entity] =>
				ReferenceStorageValue(value.value.map(_.id))
			case value: EntityInstanceReferenceValue[Entity] =>
				ReferenceStorageValue(value.value)
			case value: EnumerationEntityValue[_] =>
				StringStorageValue(value.value.map(_.toString))
		}).asInstanceOf[StorageValue]

}