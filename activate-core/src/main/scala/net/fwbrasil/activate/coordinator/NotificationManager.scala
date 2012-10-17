package net.fwbrasil.activate.coordinator

import scala.collection.mutable.{ HashMap => MutableHashMap, HashSet => MutableHashSet }
import scala.collection.mutable.ListBuffer
import net.fwbrasil.radon.util.Lockable
import net.fwbrasil.activate.util.Logging

class ContextIsAlreadyRegistered(contextId: String) extends Exception
class ContextIsntRegistered(contextId: String) extends Exception

class NotificationList extends Lockable {
	private val list =
		ListBuffer[String]()

	def take(i: Int) =
		list.take(i)

	def --=(ids: Iterable[String]) =
		ids.foreach(id => {
			val index = list.indexOf(id)
			if (index >= 0)
				list.remove(index)
			else
				throw new IllegalStateException("Can't find the notification.")
		})

	def contains(id: String) =
		list.contains(id)

	def +=(id: String) =
		list += id
}

trait NotificationManager {
	this: CoordinatorService =>

	private val notificationBlockSize =
		Integer.parseInt(
			Option(System.getProperty("activate.coordinator.notificationBlockSize"))
				.getOrElse("1000"))

	private val notifications =
		new MutableHashMap[String, NotificationList]() with Lockable

	def registerContext(contextId: String) =
		logInfo("register context " + contextId) {
			notifications.doWithWriteLock {
				if (notifications.contains(contextId))
					throw new ContextIsAlreadyRegistered(contextId)
				notifications += contextId -> new NotificationList
			}
		}

	def deregisterContext(contextId: String) =
		logInfo("deregister context " + contextId) {
			notifications.doWithWriteLock {
				if (!notifications.contains(contextId))
					throw new ContextIsntRegistered(contextId)
				notifications.remove(contextId)
			}
		}

	def getPendingNotifications(contextId: String) = {
		val list = notificationList(contextId)
		list.doWithReadLock {
			list.take(notificationBlockSize).toSet
		}
	}

	def removeNotifications(contextId: String, entityIds: Set[String]) = {
		val list = notificationList(contextId)
		list.doWithWriteLock {
			list --= entityIds
		}
	}

	private def notificationList(contextId: String) =
		notifications.doWithReadLock {
			notifications.get(contextId).getOrElse(throw new ContextIsntRegistered(contextId))
		}

	def hasPendingNotification(contextId: String, entityId: String) = {
		val list = notificationList(contextId)
		list.doWithReadLock {
			list.contains(entityId)
		}
	}

	def addNotification(originatorContextId: String, id: String) =
		notifications.doWithReadLock {
			notifications.keys.filter(_ != originatorContextId).foreach {
				contextToNotify =>
					val list = notificationList(contextToNotify)
					list.doWithWriteLock {
						list += id
					}
			}
		}

}