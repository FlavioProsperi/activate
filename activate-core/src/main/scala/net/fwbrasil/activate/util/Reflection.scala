package net.fwbrasil.activate.util

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Date
import java.util.IdentityHashMap
import org.joda.time.base.AbstractInstant
import org.objenesis.ObjenesisStd
import org.reflections.Reflections
import javassist.bytecode.LocalVariableAttribute
import javassist.ClassClassPath
import javassist.ClassPool
import javassist.CtBehavior
import net.fwbrasil.activate.entity.Entity
import javassist.CtClass
import javassist.CtPrimitiveType

object Reflection {

	val objenesis = new ObjenesisStd(false);
	val classPool = ClassPool.getDefault
	classPool.appendClassPath(new ClassClassPath(this.niceClass))

	class NiceObject[T](x: T) {
		def niceClass: Class[T] = x.getClass.asInstanceOf[Class[T]]
	}

	implicit def toNiceObject[T](x: T): NiceObject[T] = new NiceObject(x)

	class RichClass[T](clazz: Class[T]) {
		def isConcreteClass = !Modifier.isAbstract(clazz.getModifiers) && !clazz.isInterface
		def niceConstructors: List[Constructor[T]] = clazz.getConstructors.toList.asInstanceOf[List[Constructor[T]]]
	}

	implicit def toRichClass[T](clazz: Class[T]) = new RichClass(clazz)

	def newInstance[T](clazz: Class[T]): T =
		objenesis.newInstance(clazz).asInstanceOf[T]

	def getDeclaredFieldsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var fields = List[Field]()
		do {
			fields ++= clazz.getDeclaredFields()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		fields
	}

	def getDeclaredMethodsIncludingSuperClasses(concreteClass: Class[_]) = {
		var clazz = concreteClass
		var methods = List[Method]()
		do {
			methods ++= clazz.getDeclaredMethods()
			clazz = clazz.getSuperclass()
		} while (clazz != null)
		methods
	}

	def getInstanceMethods(clazz: Class[_]) =
		for (
			jMethod <- clazz.getMethods;
			if (jMethod.getDeclaringClass != classOf[Object]
				&& !Modifier.isStatic(jMethod.getModifiers))
		) yield jMethod

	def getInstanceMethodsWithoutGettersAndSetters(clazz: Class[_]) = {
		val variables = getInstanceFields(clazz).map(_.getName)
		getInstanceMethods(clazz).filter(c =>
			!variables.contains(c.getName.replace("_$eq", "")))
	}

	def getInstanceGettersAndSetters(clazz: Class[_]) = {
		val variables = getInstanceFields(clazz).map(_.getName)
		getInstanceMethods(clazz).filter(c =>
			variables.contains(c.getName.replaceAll("_$eq", "")))
	}

	def getInstanceFieldsGetterAndSetter(clazz: Class[_]) = {
		val variables = getInstanceFields(clazz)
		val methods = getInstanceMethods(clazz)
		def find(name: String) =
			methods.find(_.getName == name)
		(for (variable <- variables)
			yield (variable, find(variable.getName), find(variable.getName + "_$eq"))).toList
	}

	def getInstanceFields(clazz: Class[_]) =
		getDeclaredFieldsIncludingSuperClasses(clazz).filter(c => !Modifier.isStatic(c.getModifiers))

	def set(obj: Object, fieldName: String, value: Object) = {
		val field = getDeclaredFieldsIncludingSuperClasses(obj.niceClass).filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.set(obj, value)
	}

	def get(obj: Object, fieldName: String) = {
		val fields = getDeclaredFieldsIncludingSuperClasses(obj.niceClass)
		val field = fields.filter(_.getName() == fieldName).head
		field.setAccessible(true)
		field.get(obj)
	}

	def getStatic(obj: Class[_], fieldName: String) = {
		val fieldOption = getDeclaredFieldsIncludingSuperClasses(obj).filter(_.getName() == fieldName).headOption
		if (fieldOption.isDefined) {
			val field = fieldOption.get
			field.setAccessible(true)
			field.get(obj)
		} else null
	}

	def invoke(obj: Object, methodName: String, params: Object*) = {
		val clazz = obj.niceClass
		val method = clazz.getDeclaredMethods().filter(_.toString().contains(methodName)).head
		method.setAccessible(true)
		method.invoke(obj, params: _*)
	}

	def getAllImplementorsNames(interfaceName: String) =
		Set(new Reflections("").getStore.getSubTypesOf(interfaceName).toArray: _*).asInstanceOf[Set[String]]

	def findObject[R](obj: T forSome { type T <: Any })(f: (Any) => Boolean): Set[R] = {
		(if (f(obj))
			Set(obj)
		else
			obj match {
				case seq: Seq[Any] =>
					(for (value <- seq)
						yield findObject(value)(f)).flatten.toSet
				case obj: Product =>
					(for (elem <- obj.productElements.toList)
						yield findObject(elem)(f)).flatten.toSet
				case other =>
					Set()

			}).asInstanceOf[Set[R]]
	}

	def deepCopyMapping[T, A <: Any, B <: Any](obj: T, map: IdentityHashMap[A, B]): T = {
		val substitute = map.get(obj.asInstanceOf[A])
		if (substitute != null) {
			substitute.asInstanceOf[T]
		} else
			(obj match {
				case seq: Seq[Any] =>
					for (elem <- seq; if (elem != Nil))
						yield deepCopyMapping(elem, map)
				case obj: Enumeration#Value =>
					obj
				case obj: Entity =>
					obj
				case obj: Product =>
					val values =
						for (elem <- obj.productElements.toList)
							yield deepCopyMapping(elem, map)
					val constructors = obj.niceClass.getConstructors
					val constructorOption = constructors.headOption
					if (constructorOption.isDefined) {
						val constructor = constructorOption.get
						val newInstance = constructor.newInstance(values.asInstanceOf[Seq[Object]]: _*)
						map.put(obj.asInstanceOf[A], newInstance.asInstanceOf[B])
						newInstance
					} else obj
				case other =>
					other
			}).asInstanceOf[T]
	}

	def getObject[T](clazz: Class[_]) = {
		clazz.getField("MODULE$").get(clazz).asInstanceOf[T]
	}

	def getCompanionObject(clazz: Class[_]) = {
		val companionClassOption =
			try {
				Option(Class.forName(clazz.getName + "$"))
			} catch {
				case e: ClassNotFoundException =>
					None
			}
		companionClassOption.map(_.getField("MODULE$").get(clazz))
	}

	def materializeJodaInstant(clazz: Class[_], date: Date): AbstractInstant = {
		val constructors = clazz.getDeclaredConstructors()
		val constructor = constructors.find((c: Constructor[_]) => {
			val paramTypes = c.getParameterTypes()
			paramTypes.size == 1 && paramTypes.head.toString == "long"
		}).get
		val params: Seq[Object] = Seq(date.getTime.asInstanceOf[Object])
		val materialized = constructor.newInstance(params: _*)
		materialized.asInstanceOf[AbstractInstant]
	}

	def getParameterNamesAndTypes(method: Method): List[(String, Class[_])] = {
		val clazz = method.getDeclaringClass
		val ctClass = classPool.getCtClass(clazz.getName)
		if (ctClass.isFrozen) ctClass.defrost
		val ctMethod =
			ctClass.getMethods.find(
				m => m.getName == method.getName
					&& m.getParameterTypes.map(_.getName).toList == method.getParameterTypes.map(_.getName).toList).get
		val result = getParameterNamesAndTypes(ctMethod)
		ctClass.freeze
		result
	}

	def getParameterNamesAndTypes(constructor: Constructor[_]): List[(String, Class[_])] = {
		val clazz = constructor.getDeclaringClass
		val ctClass = classPool.getCtClass(clazz.getName)
		if (ctClass.isFrozen) ctClass.defrost
		val ctMethod =
			ctClass.getConstructors.find(
				_.getParameterTypes.map(_.getName).toList == constructor.getParameterTypes.map(_.getName).toList).get
		val result = getParameterNamesAndTypes(ctMethod)
		ctClass.freeze
		result
	}

	def getClass(ctClass: CtClass) =
		Class.forName(if (ctClass.isPrimitive())
			ctClass.asInstanceOf[CtPrimitiveType].getWrapperName()
		else
			ctClass.getName)

	def getParameterNamesAndTypes(ctBehavior: CtBehavior): List[(String, Class[_])] = {
		val types = ctBehavior.getParameterTypes
		def default =
			(for (i <- 0 until types.length)
				yield ("$" + i, getClass(types(i))))
		if (types.length > 0) {
			val codeAttribute = ctBehavior.getMethodInfo.getCodeAttribute;
			val locals = codeAttribute.getAttribute(LocalVariableAttribute.tag).asInstanceOf[LocalVariableAttribute]
			(if (locals == null) {
				default
			} else {
				val paramBeginIndexOption =
					(0 until locals.tableLength).find(i => locals.startPc(i) == 0 && !"this".equals(locals.variableName(i)))
				if (!paramBeginIndexOption.isDefined)
					default
				else {
					val paramBeginIndex = paramBeginIndexOption.get
					val paramEndIndex =
						paramBeginIndex + types.length
					(for (i <- paramBeginIndex until paramEndIndex)
						yield (locals.variableName(i).split('$')(0), getClass(types(i - paramBeginIndex)))).filter(_._1.nonEmpty)
				}
			}).toList
		} else List()
	}
}