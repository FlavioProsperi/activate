package net.fwbrasil.activate.statement

import net.fwbrasil.activate.util.CollectionUtil.combine
import net.fwbrasil.activate.entity.EntityHelper
import java.util.IdentityHashMap
import net.fwbrasil.activate.util.Reflection.deepCopyMapping
import net.fwbrasil.activate.statement.query.Query
import scala.collection.mutable.HashMap
import java.util.concurrent.ConcurrentHashMap
import com.google.common.collect.MapMaker
import com.google.common.cache.CacheBuilder
import net.fwbrasil.activate.util.ConcurrentMapCache

trait StatementNormalizer[S <: Statement] {

    val cache = new ConcurrentMapCache[S, List[S]]("StatementNormalizer.cache", 100)

    def normalize[T](statement: S): List[T] = {
        var list = cache.get(statement)
        if(list == null) {
            list = normalizeStatement(statement)
            cache.put(statement, list)
        }
        list.asInstanceOf[List[T]]
    }

    def normalizeFrom[S <: Statement](statementList: List[S]): List[S] =
        statementList.map(normalizeFrom(_)).flatten

    def normalizeFrom[S <: Statement](statement: S): List[S] = {
        val concreteClasses =
            (for (entitySource <- statement.from.entitySources)
                yield EntityHelper.concreteClasses(entitySource.entityClass).toSeq).toSeq
        val combined = combine(concreteClasses)
        val originalSources = statement.from.entitySources
        val fromMaps =
            for (classes <- combined) yield {
                val fromMap = new IdentityHashMap[Any, Any]()
                for (i <- 0 until classes.size)
                    fromMap.put(originalSources(i), EntitySource(classes(i), originalSources(i).name))
                fromMap
            }
        for (fromMap <- fromMaps) yield deepCopyMapping(statement, fromMap)
    }

    def normalizeStatement(statement: S): List[S]
}