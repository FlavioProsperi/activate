package net.fwbrasil.activate

import net.fwbrasil.activate.storage.prevayler._
import net.fwbrasil.activate.storage.relational._
import net.fwbrasil.activate.storage.memory._
import net.fwbrasil.activate.serialization.javaSerializator
import org.specs2.mutable._
import org.junit.runner._
import org.specs2.runner._
import org.specs2.execute.FailureException
import tools.scalap.scalax.rules.scalasig._
import scala.runtime._
import java.security._
import java.math.BigInteger


object runningFlag

object Test {
	def main(args: Array[String]) = {
		val md = MessageDigest.getInstance( "MD2" )
        md.update( "asasa".getBytes() )
        val hash = new BigInteger( 1, md.digest() )    
        println(hash.toString( 32 ))  
	}
}


@RunWith(classOf[JUnitRunner])
trait ActivateTest extends Specification {

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

	def executors(ctx: ActivateTestContext) =
		List(
				OneTransaction(ctx), 
				MultipleTransactions(ctx), 
				MultipleTransactionsWithReinitialize(ctx))
				
	def contexts =
		List[ActivateTestContext](
			memoryContext,
			prevaylerContext,
			//oracleContext,
			mysqlContext
		)

	def activateTest[A](f: (StepExecutor) => A) = runningFlag.synchronized {
		for (ctx <- contexts) {
			import ctx._
			def clear = transactional {
				all[ActivateTestEntity].foreach(_.delete)
			}
			for (executor <- executors(ctx)) {
				clear
				f(executor)
				executor.finalizeExecution
				clear
			}
		}
		true must beTrue
	}

	trait ActivateTestContext extends ActivateContext {
		
		override val retryLimit = 2
		
		def contextName =
			this.getClass.getSimpleName

		val fullIntValue = Option(1)
		val fullBooleanValue = Option(true)
		val fullCharValue = Option('A')
		val fullStringValue = Option("S")
		val fullLazyValue = Option("L")
		val fullFloatValue = Option(0.1f)
		val fullDoubleValue = Option(1d)
		val fullBigDecimalValue = Option(BigDecimal(1))
		val fullDateValue = Option(new java.util.Date(98977898))
		val fullCalendarValue = Option({
			val cal = java.util.Calendar.getInstance()
			cal.setTimeInMillis(98977898)
			cal
		})
		
		def fullTraitValue1 = 
			Option(all[TraitAttribute1].headOption.getOrElse({
				new TraitAttribute1("1")
			}))
		
		def fullTraitValue2 = 
			Option(all[TraitAttribute2].headOption.getOrElse({
				new TraitAttribute2("2")
			}))
		
		val fullByteArrayValue = Option("S".getBytes)
		def fullEntityValue =
 			Option(allWhere[ActivateTestEntity](_.dummy :== true).headOption.getOrElse({
				val entity = newEmptyActivateTestEntity
				entity.dummy := true
				entity
			}))

		def setFullEntity(entity: ActivateTestEntity) = {
			entity.intValue.put(fullIntValue)
			entity.booleanValue.put(fullBooleanValue)
			entity.charValue.put(fullCharValue)
			entity.stringValue.put(fullStringValue)
			entity.floatValue.put(fullFloatValue)
			entity.doubleValue.put(fullDoubleValue)
			entity.bigDecimalValue.put(fullBigDecimalValue)
			entity.dateValue.put(fullDateValue)
			entity.calendarValue.put(fullCalendarValue)
			entity.byteArrayValue.put(fullByteArrayValue)
			entity.entityValue.put(fullEntityValue)
			entity.traitValue1.put(fullTraitValue1)
			entity.traitValue2.put(fullTraitValue2)
			entity.lazyValue.put(fullLazyValue)
			entity
		}

		def setEmptyEntity(entity: ActivateTestEntity) = {
			entity.intValue.put(None)
			entity.booleanValue.put(None)
			entity.charValue.put(None)
			entity.stringValue.put(None)
			entity.floatValue.put(None)
			entity.doubleValue.put(None)
			entity.bigDecimalValue.put(None)
			entity.dateValue.put(None)
			entity.calendarValue.put(None)
			entity.byteArrayValue.put(None)
			entity.entityValue.put(None)
			entity.traitValue1.put(None)
			entity.traitValue2.put(None)
			entity.lazyValue.put(None)
			entity
		}

		def newEmptyActivateTestEntity =
			setEmptyEntity(newTestEntity())
		def newFullActivateTestEntity =
			setFullEntity(newTestEntity())
			
		trait TraitAttribute extends Entity{
			def testTraitAttribute
		}
		
		case class TraitAttribute1(attribute: Var[String]) extends TraitAttribute {
			def testTraitAttribute = attribute.get
		}
		
		case class TraitAttribute2(attribute: Var[String]) extends TraitAttribute {
			def testTraitAttribute = attribute.get
		}
			
		class ActivateTestEntity(
			val dummy: Var[Boolean] = false,
			val intValue: Var[Int],
			val booleanValue: Var[Boolean],
			val charValue: Var[Char],
			val stringValue: Var[String],
			val floatValue: Var[Float],
			val doubleValue: Var[Double],
			val bigDecimalValue: Var[BigDecimal],
			val dateValue: Var[java.util.Date],
			val calendarValue: Var[java.util.Calendar],
			val byteArrayValue: Var[Array[Byte]],
			val entityValue: Var[ActivateTestEntity],
			val traitValue1: Var[TraitAttribute],
			val traitValue2: Var[TraitAttribute],
			lazyValueValue: Var[String]) extends Entity {
			lazy val lazyValue: Var[String] = lazyValueValue
		}

		def validateFullTestEntity(entity: ActivateTestEntity = null,
			intValue: Option[Int] = fullIntValue,
			booleanValue: Option[Boolean] = fullBooleanValue,
			charValue: Option[Char] = fullCharValue,
			stringValue: Option[String] = fullStringValue,
			floatValue: Option[Float] = fullFloatValue,
			doubleValue: Option[Double] = fullDoubleValue,
			bigDecimalValue: Option[BigDecimal] = fullBigDecimalValue,
			dateValue: Option[java.util.Date] = fullDateValue,
			calendarValue: Option[java.util.Calendar] = fullCalendarValue,
			byteArrayValue: Option[Array[Byte]] = fullByteArrayValue,
			entityValue: Option[ActivateTestEntity] = fullEntityValue,
			traitValue1: Option[TraitAttribute] = fullTraitValue1,
			traitValue2: Option[TraitAttribute] = fullTraitValue2,
			lazyValue: Option[String] = fullLazyValue) =

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
			intValue: Option[Int] = None,
			booleanValue: Option[Boolean] = None,
			charValue: Option[Char] = None,
			stringValue: Option[String] = None,
			floatValue: Option[Float] = None,
			doubleValue: Option[Double] = None,
			bigDecimalValue: Option[BigDecimal] = None,
			dateValue: Option[java.util.Date] = None,
			calendarValue: Option[java.util.Calendar] = None,
			byteArrayValue: Option[Array[Byte]] = None,
			entityValue: Option[ActivateTestEntity] = None,
			traitValue1: Option[TraitAttribute] = None,
			traitValue2: Option[TraitAttribute] = None,
			lazyValue: Option[String] = None) = {

			entity.intValue.get must beEqualTo(intValue)
			entity.booleanValue.get must beEqualTo(booleanValue)
			entity.charValue.get must beEqualTo(charValue)
			entity.stringValue.get must beEqualTo(stringValue)
			entity.floatValue.get must beEqualTo(floatValue)
			entity.doubleValue.get must beEqualTo(doubleValue)
			entity.bigDecimalValue.get must beEqualTo(bigDecimalValue)
			entity.dateValue.get must beEqualTo(dateValue)
			entity.calendarValue.get must beEqualTo(calendarValue)
			entity.entityValue.get must beEqualTo(entityValue)
			entity.traitValue1.get must beEqualTo(traitValue1)
			entity.traitValue2.get must beEqualTo(traitValue2)
		}

		def newTestEntity(intValue: Option[Int] = None,
			booleanValue: Option[Boolean] = None,
			charValue: Option[Char] = None,
			stringValue: Option[String] = None,
			floatValue: Option[Float] = None,
			doubleValue: Option[Double] = None,
			bigDecimalValue: Option[BigDecimal] = None,
			dateValue: Option[java.util.Date] = None,
			calendarValue: Option[java.util.Calendar] = None,
			byteArrayValue: Option[Array[Byte]] = None,
			entityValue: Option[ActivateTestEntity] = None,
			traitValue1: Option[TraitAttribute] = None,
			traitValue2: Option[TraitAttribute] = None,
			lazyValue: Option[String] = None) =
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
				lazyValueValue = lazyValue)
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
			val user = "ACTIVATE"
			val password = "ACTIVATE"
			val url = "jdbc:oracle:thin:@localhost:1521:oracle"
			val dialect = oracleDialect
			val serializator = javaSerializator
		}
	}

	object mysqlContext extends ActivateTestContext {
		val storage = new SimpleJdbcRelationalStorage {
			val jdbcDriver = "com.mysql.jdbc.Driver"
			val user = "root"
			val password = "root"
			val url = "jdbc:mysql://127.0.0.1/teste"
			val dialect = mySqlDialect
			val serializator = javaSerializator
		}
	}

	
}