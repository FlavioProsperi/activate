package net.fwbrasil.activate.entity

import net.fwbrasil.activate.util.uuid.UUIDUtil

object IdVar {
    def generateId(entityClass: Class[_]) = {
        val uuid = UUIDUtil.generateUUID
        val classId = EntityHelper.getEntityClassHashId(entityClass)
        uuid + "-" + classId
    }
}

class IdVar(metadata: EntityPropertyMetadata, outerEntity: Entity, val entityId: String)
        extends Var[String](metadata, outerEntity, false) {

    def this(metadata: EntityPropertyMetadata, outerEntity: Entity) =
        this(metadata, outerEntity, IdVar.generateId(outerEntity.getClass))

    super.put(Option(entityId))

    override def getValue() =
        entityId

    override def get =
        Some(entityId)

    override def put(value: Option[String]) = {}

    override protected def doInitialized[A](f: => A): A = {
        f
    }

    override def toString = "id -> " + entityId
}