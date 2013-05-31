package net.fwbrasil.activate.spray.json

import net.fwbrasil.activate.util.Reflection._
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.entity.SerializableEntityValue
import net.fwbrasil.activate.entity.ListEntityValue
import net.fwbrasil.activate.entity.LazyListEntityValue
import net.fwbrasil.activate.entity.EnumerationEntityValue
import net.fwbrasil.activate.entity.DateEntityValue
import net.fwbrasil.activate.entity.FloatEntityValue
import net.fwbrasil.activate.entity.EntityInstanceEntityValue
import net.fwbrasil.activate.entity.CharEntityValue
import net.fwbrasil.activate.entity.DoubleEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.entity.LongEntityValue
import net.fwbrasil.activate.entity.StringEntityValue
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.BooleanEntityValue
import net.fwbrasil.activate.entity.ByteArrayEntityValue
import net.fwbrasil.activate.entity.BigDecimalEntityValue
import net.fwbrasil.activate.entity.EntityInstanceReferenceValue
import net.fwbrasil.activate.entity.CalendarEntityValue
import net.fwbrasil.activate.entity.EntityValue
import java.util.Date
import org.joda.time.Instant
import java.util.Calendar
import net.fwbrasil.activate.entity.IdVar
import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.EntityHelper
import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.entity.IntEntityValue
import net.fwbrasil.activate.entity.JodaInstantEntityValue
import net.fwbrasil.activate.entity.LazyList
import org.joda.time.base.AbstractInstant
import java.text.SimpleDateFormat
import java.text.DateFormat
import spray.json._
import DefaultJsonProtocol._
import java.nio.charset.Charset

trait SprayJsonContext {
    this: ActivateContext =>

    val jsonCharset = Charset.defaultCharset

    private def toJsValue[A](entityValue: EntityValue[A], f: A => JsValue): JsValue =
        entityValue.value.map(f(_)).getOrElse(JsNull)

    protected def jsonDateFormat =
        new SimpleDateFormat("EEE MMM dd HH:mm:ss:SSS zzz yyyy")

    private def fromJsValue(value: JsValue, entityValue: EntityValue[_]): Any =
        (value, entityValue) match {
            case (JsNull, entityValue) =>
                null
            case (JsNumber(value), entityValue: IntEntityValue) =>
                value.intValue
            case (JsNumber(value), entityValue: LongEntityValue) =>
                value.longValue
            case (JsBoolean(value), entityValue: BooleanEntityValue) =>
                value
            case (JsString(value), entityValue: CharEntityValue) =>
                value.charAt(0)
            case (JsString(value), entityValue: StringEntityValue) =>
                value
            case (JsNumber(value), entityValue: FloatEntityValue) =>
                value.floatValue
            case (JsNumber(value), entityValue: DoubleEntityValue) =>
                value.doubleValue
            case (JsNumber(value), entityValue: BigDecimalEntityValue) =>
                value
            case (JsString(value), entityValue: DateEntityValue) =>
                jsonDateFormat.parse(value)
            case (JsString(value), entityValue: JodaInstantEntityValue[_]) =>
                materializeJodaInstant(entityValue.instantClass, jsonDateFormat.parse(value))
            case (JsString(value), entityValue: CalendarEntityValue) =>
                val calendar = Calendar.getInstance
                calendar.setTime(jsonDateFormat.parse(value))
                calendar
            case (JsString(value), entityValue: ByteArrayEntityValue) =>
                value.getBytes(jsonCharset)
            case (JsString(value), entityValue: EntityInstanceEntityValue[_]) =>
                byId(value).getOrElse(throw new IllegalStateException("Invalid id " + value))
            case (JsString(value), entityValue: EntityInstanceReferenceValue[_]) =>
                byId(value).getOrElse(throw new IllegalStateException("Invalid id " + value))
            case (JsString(value), entityValue: EnumerationEntityValue[_]) =>
                val enumerationValueClass = entityValue.enumerationClass
                val enumerationClass = enumerationValueClass.getEnclosingClass
                val enumerationObjectClass = Class.forName(enumerationClass.getName + "$")
                val obj = getObject[Enumeration](enumerationObjectClass)
                obj.withName(value)
            case (JsArray(value), entityValue: ListEntityValue[_]) =>
                value.toList.map(fromJsValue(_, entityValue.emptyValueEntityValue))
            case (JsArray(value), entityValue: LazyListEntityValue[_]) =>
                new LazyList[Entity](value.toList.collect { case JsString(id) => id })(entityValue.valueManifest.asInstanceOf[Manifest[Entity]])
            case (JsString(value), entityValue: SerializableEntityValue[_]) =>
                entityValue.serializator.fromSerialized(value.getBytes)(entityValue.typeManifest)
        }

    private def toJsValue(entityValue: EntityValue[_]): JsValue =
        entityValue match {
            case value: IntEntityValue =>
                toJsValue[Int](value, JsNumber(_))
            case value: LongEntityValue =>
                toJsValue[Long](value, JsNumber(_))
            case value: BooleanEntityValue =>
                toJsValue[Boolean](value, JsBoolean(_))
            case value: CharEntityValue =>
                toJsValue[Char](value, c => JsString(c.toString))
            case value: StringEntityValue =>
                toJsValue[String](value, JsString(_))
            case value: FloatEntityValue =>
                toJsValue[Float](value, JsNumber(_))
            case value: DoubleEntityValue =>
                toJsValue[Double](value, JsNumber(_))
            case value: BigDecimalEntityValue =>
                toJsValue[BigDecimal](value, JsNumber(_))
            case value: DateEntityValue =>
                toJsValue[Date](value, d => JsString(jsonDateFormat.format(d)))
            case value: JodaInstantEntityValue[AbstractInstant] =>
                toJsValue[AbstractInstant](value, d => JsString(jsonDateFormat.format(d.toDate)))
            case value: CalendarEntityValue =>
                toJsValue[Calendar](value, d => JsString(jsonDateFormat.format(d.getTime)))
            case value: ByteArrayEntityValue =>
                toJsValue[Array[Byte]](value, b => JsString(new String(b)))
            case value: EntityInstanceEntityValue[Entity] =>
                toJsValue[Entity](value, e => JsString(e.id))
            case value: EntityInstanceReferenceValue[Entity] =>
                toJsValue[String](value, JsString(_))
            case value: EnumerationEntityValue[Enumeration#Value] =>
                toJsValue[Enumeration#Value](value, e => JsString(e.toString))
            case value: ListEntityValue[Any] =>
                value.value.map(list =>
                    JsArray(list.map(e => toJsValue(value.valueEntityValue(e)))))
                    .getOrElse(JsNull)
            case value: LazyListEntityValue[_] =>
                value.value.map(list =>
                    JsArray(list.ids.map(JsString(_))))
                    .getOrElse(JsNull)
            case value: SerializableEntityValue[_] =>
                value.value.map(v =>
                    JsString(new String(value.serializator.toSerialized(v)(value.typeManifest))))
                    .getOrElse(JsNull)
        }

    private def updateFromJsonObject(entity: Entity, jsObject: JsObject) = {
        val fields = jsObject.fields
        val entityClass = entity.getClass
        val entityMetadata =
            EntityHelper.metadatas.find(_.entityClass == entityClass).get
        val propMetadataMap =
            entityMetadata.propertiesMetadata.mapBy(_.name)
        for ((name, jdValue) <- fields if (name != "id")) {
            val property = propMetadataMap(name)
            val ref = entity.varNamed(name)
            val entityValue = ref.toEntityPropertyValue(None)
            val value = fromJsValue(jdValue, entityValue)
            val propertyMetadata = propMetadataMap(name)
            if (propertyMetadata.isOption)
                ref.put(Option(value))
            else
                ref.putValue(value)
        }
        entity
    }

    def createEntityFromJson[E <: Entity: Manifest](json: String): E =
        createEntityFromJson[E](json.asJson.asJsObject)

    def createEntityFromJson[E <: Entity: Manifest](jsValue: JsValue): E = {
        val entityClass = erasureOf[E]
        val id = IdVar.generateId(entityClass)
        val entity = liveCache.createLazyEntity(entityClass, id)
        entity.setInitialized
        entity.setNotPersisted
        context.liveCache.toCache(entityClass, () => entity)
        updateFromJsonObject(entity, jsValue.asJsObject)
        entity
    }

    def createOrUpdateEntityFromJson[E <: Entity: Manifest](json: String): E =
        createOrUpdateEntityFromJson[E](json.asJson.asJsObject)

    def createOrUpdateEntityFromJson[E <: Entity: Manifest](jsValue: JsValue) = {
        jsValue.asJsObject.fields.collect {
            case ("id", JsString(id)) =>
                val entity = byId[E](id).getOrElse(throw new IllegalStateException("Invalid id " + id))
                updateFromJsonObject(entity, jsValue.asJsObject)
                entity
        }.headOption.getOrElse {
            createEntityFromJson[E](jsValue.asJsObject)
        }
    }

    private def toJsonObject[E <: Entity: Manifest](entity: E) = {
        val fields =
            entity.vars.filter(!_.isTransient).map(ref =>
                (ref.name, ref.get.map(value => toJsValue(ref.toEntityPropertyValue(value))).getOrElse(JsNull))).toMap
        JsObject(fields)
    }

    implicit def entityJsonFormat[E <: Entity: Manifest] =
        new RootJsonFormat[E] {
            def write(entity: E) =
                toJsonObject[E](entity)

            def read(value: JsValue) =
                createOrUpdateEntityFromJson[E](value)
        }

    implicit class EntityJsonMethods[E <: Entity: Manifest](val entity: E) {

        def updateFromJson(json: String): Unit =
            updateFromJson(json.toJson)
        
        def updateFromJson(json: JsValue) =
            updateFromJsonObject(entity, json.asJsObject)
    }

}