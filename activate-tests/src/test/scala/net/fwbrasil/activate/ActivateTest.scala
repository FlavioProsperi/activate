package net.fwbrasil.activate

import net.fwbrasil.activate.storage.prevayler._
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.storage.memory._
import net.fwbrasil.activate.serialization.javaSerializator
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import org.specs2.execute.FailureException
import scala.runtime._
import java.security._
import java.math.BigInteger

object runningFlag

object EnumerationValue extends Enumeration {
	case class EnumerationValue(name: String) extends Val(name)
	val value1a = EnumerationValue("v1")
	val value2 = EnumerationValue("v2")
	val value3 = EnumerationValue("v3")
}

import EnumerationValue._

//@RunWith(classOf[JUnitRunner])
trait ActivateTest extends SpecificationWithJUnit {

	trait StepExecutor {
		def apply[A](step: => A): A
		def finalizeExecution = {

		}
		def execute[A](step: => A): A =
			try {
				step
			} catch {
				case e: FailureException =>
					throw new IllegalStateException(e.f + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
				case e =>
					e.printStackTrace
					throw new IllegalStateException(e.getMessage + " (ctx: " + contextName + ", mode: " + modeName + ")", e)
			}
		def contextName = ctx.name
		val modeName = this.getClass.getSimpleName
		val ctx: ActivateTestContext
	}

	case class OneTransaction(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		val transaction = new Transaction
		def apply[A](s: => A): A = execute {
			transactional(transaction) {
				s
			}
		}
		override def finalizeExecution = {
			transaction.commit
		}
	}

	case class MultipleTransactions(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		def apply[A](s: => A): A = execute {
			transactional {
				s
			}
		}
	}

	case class MultipleTransactionsWithReinitialize(ctx: ActivateTestContext) extends StepExecutor {
		import ctx._
		def apply[A](s: => A): A = execute {
			val ret =
				transactional {
					s
				}
			reinitializeContext
			ret
		}
	}

	def executors(ctx: ActivateTestContext): List[StepExecutor] =
		List(
			OneTransaction(ctx),
			MultipleTransactions(ctx),
			MultipleTransactionsWithReinitialize(ctx))

	def contexts = {
		val ret = List[ActivateTestContext](
			memoryContext,
			//			prevaylerContext,
			//			oracleContext,
			mysqlContext)
		ret.foreach(_.stop)
		ret
	}

	def activateTest[A](f: (StepExecutor) => A) = runningFlag.synchronized {

		for (ctx <- contexts) {
			import ctx._
			start
				def clear = transactional {
					all[ActivateTestEntity].foreach(_.delete)
				}
			for (executor <- executors(ctx)) {
				clear
				f(executor)
				executor.finalizeExecution
				clear
			}
			stop
		}
		true must beTrue
	}

	trait ActivateTestContext extends ActivateContext {

		override val retryLimit = 2

		def contextName =
			this.getClass.getSimpleName

		val emptyIntValue = 0
		val emptyBooleanValue = false
		val emptyCharValue = 'N'
		val emptyStringValue = null
		val emptyLazyValue = null
		val emptyFloatValue = 0f
		val emptyDoubleValue = 0d
		val emptyBigDecimalValue = null
		val emptyDateValue = null
		val emptyCalendarValue = null
		def emptyTraitValue1 = null
		val emptyTraitValue2 = null
		val emptyByteArrayValue = Array[Byte]()
		val emptyEntityValue = null
		val emptyEnumerationValue = null

		val fullIntValue = 999
		val fullBooleanValue = true
		val fullCharValue = 'A'
		val fullStringValue = "S"
		val fullLazyValue = "L"
		val fullFloatValue = 0.1f
		val fullDoubleValue = 1d
		val fullBigDecimalValue = BigDecimal(1)
		val fullDateValue = new java.util.Date(98977898)
		val fullEnumerationValue = EnumerationValue.value1a
		val fullCalendarValue = {
			val cal = java.util.Calendar.getInstance()
			cal.setTimeInMillis(98977898)
			cal
		}

		def fullTraitValue1 =
			all[TraitAttribute1].headOption.getOrElse({
				new TraitAttribute1("1")
			})

		def fullTraitValue2 =
			all[TraitAttribute2].headOption.getOrElse({
				new TraitAttribute2("2")
			})

		val fullByteArrayValue = "S".getBytes
		def fullEntityValue =
			allWhere[ActivateTestEntity](_.dummy :== true).headOption.getOrElse({
				val entity = newEmptyActivateTestEntity
				entity.dummy = true
				entity
			})

		def setFullEntity(entity: ActivateTestEntity) = {
			entity.intValue = fullIntValue
			entity.booleanValue = fullBooleanValue
			entity.charValue = fullCharValue
			entity.stringValue = fullStringValue
			entity.floatValue = fullFloatValue
			entity.doubleValue = fullDoubleValue
			entity.bigDecimalValue = fullBigDecimalValue
			entity.dateValue = fullDateValue
			entity.calendarValue = fullCalendarValue
			entity.byteArrayValue = fullByteArrayValue
			entity.entityValue = fullEntityValue
			entity.traitValue1 = fullTraitValue1
			entity.traitValue2 = fullTraitValue2
			entity.enumerationValue = fullEnumerationValue
			entity
		}

		def setEmptyEntity(entity: ActivateTestEntity) = {
			entity.intValue = emptyIntValue
			entity.booleanValue = emptyBooleanValue
			entity.charValue = emptyCharValue
			entity.stringValue = emptyStringValue
			entity.floatValue = emptyFloatValue
			entity.doubleValue = emptyDoubleValue
			entity.bigDecimalValue = emptyBigDecimalValue
			entity.dateValue = emptyDateValue
			entity.calendarValue = emptyCalendarValue
			entity.byteArrayValue = emptyByteArrayValue
			entity.entityValue = emptyEntityValue
			entity.traitValue1 = emptyTraitValue1
			entity.traitValue2 = emptyTraitValue2
			entity.enumerationValue = emptyEnumerationValue
			entity
		}

		def newEmptyActivateTestEntity =
			setEmptyEntity(newTestEntity())
		def newFullActivateTestEntity =
			setFullEntity(newTestEntity())

		trait TraitAttribute extends Entity {
			def attribute: String
		}

		class TraitAttribute1(val attribute: String) extends TraitAttribute {
			def testTraitAttribute = attribute
		}

		class TraitAttribute2(val attribute: String) extends TraitAttribute {
			def testTraitAttribute = attribute
		}

		class ActivateTestEntity(
			var dummy: Boolean = false,
			var intValue: Int,
			var booleanValue: Boolean,
			var charValue: Char,
			var stringValue: String,
			var floatValue: Float,
			var doubleValue: Double,
			var bigDecimalValue: BigDecimal,
			var dateValue: java.util.Date,
			var calendarValue: java.util.Calendar,
			var byteArrayValue: Array[Byte],
			var entityValue: ActivateTestEntity,
			var traitValue1: TraitAttribute,
			var traitValue2: TraitAttribute,
			var enumerationValue: EnumerationValue,
			lazyValueValue: String) extends Entity {
			lazy val lazyValue: String = lazyValueValue
		}

		def validateFullTestEntity(entity: ActivateTestEntity = null,
			intValue: Int = fullIntValue,
			booleanValue: Boolean = fullBooleanValue,
			charValue: Char = fullCharValue,
			stringValue: String = fullStringValue,
			floatValue: Float = fullFloatValue,
			doubleValue: Double = fullDoubleValue,
			bigDecimalValue: BigDecimal = fullBigDecimalValue,
			dateValue: java.util.Date = fullDateValue,
			calendarValue: java.util.Calendar = fullCalendarValue,
			byteArrayValue: Array[Byte] = fullByteArrayValue,
			entityValue: ActivateTestEntity = fullEntityValue,
			traitValue1: TraitAttribute = fullTraitValue1,
			traitValue2: TraitAttribute = fullTraitValue2,
			lazyValue: String = fullLazyValue) =

			validateEmptyTestEntity(
				entity,
				intValue,
				booleanValue,
				charValue,
				stringValue,
				floatValue,
				doubleValue,
				bigDecimalValue,
				dateValue,
				calendarValue,
				byteArrayValue,
				entityValue,
				traitValue1,
				traitValue2,
				lazyValue)

		def validateEmptyTestEntity(entity: ActivateTestEntity = null,
			intValue: Int = emptyIntValue,
			booleanValue: Boolean = emptyBooleanValue,
			charValue: Char = emptyCharValue,
			stringValue: String = emptyStringValue,
			floatValue: Float = emptyFloatValue,
			doubleValue: Double = emptyDoubleValue,
			bigDecimalValue: BigDecimal = emptyBigDecimalValue,
			dateValue: java.util.Date = emptyDateValue,
			calendarValue: java.util.Calendar = emptyCalendarValue,
			byteArrayValue: Array[Byte] = emptyByteArrayValue,
			entityValue: ActivateTestEntity = emptyEntityValue,
			traitValue1: TraitAttribute = emptyTraitValue1,
			traitValue2: TraitAttribute = emptyTraitValue2,
			lazyValue: String = emptyLazyValue) = {

			entity.intValue must beEqualTo(intValue)
			entity.booleanValue must beEqualTo(booleanValue)
			entity.charValue must beEqualTo(charValue)
			entity.stringValue must beEqualTo(stringValue)
			entity.floatValue must beEqualTo(floatValue)
			entity.doubleValue must beEqualTo(doubleValue)
			entity.bigDecimalValue must beEqualTo(bigDecimalValue)
			entity.dateValue must beEqualTo(dateValue)
			entity.calendarValue must beEqualTo(calendarValue)
			entity.entityValue must beEqualTo(entityValue)
			entity.traitValue1 must beEqualTo(traitValue1)
			entity.traitValue2 must beEqualTo(traitValue2)
		}

		def newTestEntity(
			intValue: Int = emptyIntValue,
			booleanValue: Boolean = emptyBooleanValue,
			charValue: Char = emptyCharValue,
			stringValue: String = emptyStringValue,
			floatValue: Float = emptyFloatValue,
			doubleValue: Double = emptyDoubleValue,
			bigDecimalValue: BigDecimal = emptyBigDecimalValue,
			dateValue: java.util.Date = emptyDateValue,
			calendarValue: java.util.Calendar = emptyCalendarValue,
			byteArrayValue: Array[Byte] = emptyByteArrayValue,
			entityValue: ActivateTestEntity = emptyEntityValue,
			traitValue1: TraitAttribute = emptyTraitValue1,
			traitValue2: TraitAttribute = emptyTraitValue2,
			enumerationValue: EnumerationValue = emptyEnumerationValue,
			lazyValue: String = emptyLazyValue) = {
			new ActivateTestEntity(
				intValue = intValue,
				booleanValue = booleanValue,
				charValue = charValue,
				stringValue = stringValue,
				floatValue = floatValue,
				doubleValue = doubleValue,
				bigDecimalValue = bigDecimalValue,
				dateValue = dateValue,
				calendarValue = calendarValue,
				byteArrayValue = byteArrayValue,
				entityValue = entityValue,
				traitValue1 = traitValue1,
				traitValue2 = traitValue2,
				enumerationValue = enumerationValue,
				lazyValueValue = lazyValue)
		}
	}

	object prevaylerContext extends ActivateTestContext {
		val storage = new PrevaylerMemoryStorage {
			override lazy val name = "test/PrevalenceBase/testPrevaylerMemoryStorage" + (new java.util.Date).getTime
		}
	}

	object memoryContext extends ActivateTestContext {
		val storage = new MemoryStorage {}
	}

	object oracleContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "oracle.jdbc.driver.OracleDriver"
			val user = "ACTIVATE_TEST"
			val password = "ACTIVATE_TEST"
			val url = "jdbc:oracle:thin:@10.211.55.3:1521:oracle"
			val dialect = oracleDialect
			val serializator = javaSerializator
		}
	}

	object mysqlContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = ""
			val url = "jdbc:mysql://127.0.0.1/teste"
			val dialect = mySqlDialect
			val serializator = javaSerializator
		}
	}

}