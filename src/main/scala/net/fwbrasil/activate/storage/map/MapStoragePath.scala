package net.fwbrasil.activate.storage.map

import net.fwbrasil.activate.util.ManifestUtil._
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.entity.Var

abstract case class MapStoragePath

case class EntityClassPath
	[E <: Entity: Manifest]
	extends MapStoragePath {
	def entityClass = manifest[E].erasure
	def path: List[Any] = entityClass.getCanonicalName :: Nil
	override def toString = path.reverse.mkString("/") 
}

case class EntityInstancePath
	[E <: Entity: Manifest]
	(entityId: String)
	extends EntityClassPath[E] {
	override def path = entityId :: super.path 
}

case class EntityPropetyPath
	[E <: Entity: Manifest,
	 P: Manifest]
	(override val entityId: String, propertyName: String)
	extends EntityInstancePath[E](entityId) {
	def propertyClass = manifest[P].erasure
	override def path = propertyName :: super.path
}

object MapStoragePath {
	
	def apply[P: Manifest](ref: Var[P]) =
		EntityPropetyPath(ref.outerEntity.id, ref.name)(ref.outerEntityClass, manifest[P])
	
	def apply[E <: Entity: Manifest](entity: E) =
		EntityInstancePath[E](entity.id)
		
	def apply[E <: Entity: Manifest](id: String) = 
		EntityInstancePath[E](id)
		
	def apply[E <: Entity: Manifest] = 
		EntityClassPath[E]
}