package net.fwbrasil.activate.statement.query

import java.util.Calendar
import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import scala.util.Random.nextDouble
import scala.util.Random.nextFloat
import scala.util.Random.nextInt
import scala.util.Random.nextLong
import net.fwbrasil.activate.oracleContext

@RunWith(classOf[JUnitRunner])
class OrderedQuerySpecs extends ActivateTest {

    "Query framework" should {
        "perform ordered queries" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        def randomCalendar = {
                            val calendar = Calendar.getInstance
                            calendar.setTimeInMillis(nextInt)
                            calendar
                        }
                        for (i <- 0 until 30)
                            newTestEntity(
                                intValue = nextInt,
                                longValue = nextLong,
                                floatValue = nextFloat,
                                doubleValue = nextDouble,
                                dateValue = new Date(nextInt),
                                jodaInstantValue = new DateTime(nextInt),
                                calendarValue = randomCalendar,
                                stringValue = nextFloat.toString,
                                optionValue = if (i == 10) None else Option(nextInt.toString))
                    }
                    def entities =
                        all[ActivateTestEntity].toList
                    step {
                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.intValue)
                        } must beEqualTo(entities.sortBy(_.intValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.longValue)
                        } must beEqualTo(entities.sortBy(_.longValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.floatValue)
                        } must beEqualTo(entities.sortBy(_.floatValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.doubleValue)
                        } must beEqualTo(entities.sortBy(_.doubleValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.dateValue)
                        } must beEqualTo(entities.sortBy(_.dateValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue)
                        } must beEqualTo(entities.sortBy(_.jodaInstantValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.calendarValue)
                        } must beEqualTo(entities.sortBy(_.calendarValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.stringValue)
                        } must beEqualTo(entities.sortBy(_.stringValue))

                    }

                    step {
                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.intValue asc)
                        } must beEqualTo(entities.sortBy(_.intValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.longValue asc)
                        } must beEqualTo(entities.sortBy(_.longValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.floatValue asc)
                        } must beEqualTo(entities.sortBy(_.floatValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.doubleValue asc)
                        } must beEqualTo(entities.sortBy(_.doubleValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.dateValue asc)
                        } must beEqualTo(entities.sortBy(_.dateValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue asc)
                        } must beEqualTo(entities.sortBy(_.jodaInstantValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.calendarValue asc)
                        } must beEqualTo(entities.sortBy(_.calendarValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.stringValue asc)
                        } must beEqualTo(entities.sortBy(_.stringValue))

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.optionValue asc)
                        } must beEqualTo(entities.sortBy(_.optionValue))

                    }

                    step {
                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.intValue desc)
                        } must beEqualTo(entities.sortBy(_.intValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.longValue desc)
                        } must beEqualTo(entities.sortBy(_.longValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.floatValue desc)
                        } must beEqualTo(entities.sortBy(_.floatValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.doubleValue desc)
                        } must beEqualTo(entities.sortBy(_.doubleValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.dateValue desc)
                        } must beEqualTo(entities.sortBy(_.dateValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.jodaInstantValue desc)
                        } must beEqualTo(entities.sortBy(_.jodaInstantValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.calendarValue desc)
                        } must beEqualTo(entities.sortBy(_.calendarValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.stringValue desc)
                        } must beEqualTo(entities.sortBy(_.stringValue).reverse)

                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.optionValue desc)
                        } must beEqualTo(entities.sortBy(_.optionValue).reverse)

                    }

                })
        }

        "perform query with multiple order by" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val expected = List((1, 1), (1, 2), (2, 1), (2, 2), (2, 4), (2, 5), (3, 1), (4, 1))
                    step {
                        expected.randomize.foreach {
                            case (intValue, longValue) =>
                                val entity = newEmptyActivateTestEntity
                                entity.intValue = intValue
                                entity.longValue = longValue
                        }
                    }
                    step {
                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.intValue, entity.longValue)
                        }.map(entity => (entity.intValue, entity.longValue)).toList must beEqualTo(expected)
                    }
                })
        }

        "perform query with order by null column" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val expected = List(null, "a", "b", "c")
                    step {
                        expected.randomize.foreach(v =>
                            newEmptyActivateTestEntity.stringValue = v)
                    }
                    step {
                        query {
                            (entity: ActivateTestEntity) =>
                                where(entity isNotNull) select (entity) orderBy (entity.stringValue)
                        }.toList.map(_.stringValue) must beEqualTo(expected)
                    }
                })
        }

        "perform normalized query with order by" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        new TraitAttribute1("a")
                        new TraitAttribute2("b")
                        new TraitAttribute1("c")
                        new TraitAttribute2("d")
                    }
                    step {
                        query {
                            (e: TraitAttribute) => where(e isNotNull) select (e.attribute) orderBy (e.attribute)
                        } mustEqual (List("a", "b", "c", "d"))
                    }
                })
        }

        "support limit" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != oracleContext) {
                        val expected = List(null, "a", "b", "c")
                        step {
                            expected.randomize.foreach(v =>
                                newEmptyActivateTestEntity.stringValue = v)
                        }
                        step {
                            query {
                                (entity: ActivateTestEntity) =>
                                    where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (2)
                            }.toList.map(_.stringValue) must beEqualTo(List(null, "a"))
                            query {
                                (entity: ActivateTestEntity) =>
                                    where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (10)
                            }.toList.map(_.stringValue) must beEqualTo(List(null, "a", "b", "c"))
                        }
                    }
                })
        }

        "do not cache the limit value" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (step.ctx != oracleContext) {
                        val expected = List(null, "a", "b", "c")
                        step {
                            expected.randomize.foreach(v =>
                                newEmptyActivateTestEntity.stringValue = v)
                        }
                        step {
                            def runQuery(limit: Int) =
                                query {
                                    (entity: ActivateTestEntity) =>
                                        where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (limit)
                                }

                            runQuery(limit = 2).toList.map(_.stringValue) must beEqualTo(List(null, "a"))
                            runQuery(limit = 10).toList.map(_.stringValue) must beEqualTo(List(null, "a", "b", "c"))
                        }
                    }
                })
        }

        "support limit with offset" in {
            activateTest(
                (step: StepExecutor) => {
                    if (!step.isInstanceOf[OneTransaction] && step.ctx != oracleContext) {
                        import step.ctx._
                        val expected = List(null, "a", "b", "c")
                        step {
                            expected.randomize.foreach(v =>
                                newEmptyActivateTestEntity.stringValue = v)
                        }
                        step {
                            query {
                                (entity: ActivateTestEntity) =>
                                    where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (2) offset (1)
                            }.toList.map(_.stringValue) must beEqualTo(List("a", "b"))
                            query {
                                (entity: ActivateTestEntity) =>
                                    where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (10) offset (2)
                            }.toList.map(_.stringValue) must beEqualTo(List("b", "c"))
                        }
                    }
                })
        }
        
        "fo not cache the offset value" in {
            activateTest(
                (step: StepExecutor) => {
                    if (!step.isInstanceOf[OneTransaction] && step.ctx != oracleContext) {
                        import step.ctx._
                        val expected = List(null, "a", "b", "c")
                        step {
                            expected.randomize.foreach(v =>
                                newEmptyActivateTestEntity.stringValue = v)
                        }
                        step {
                            def runQuery(limit: Int, offset: Int) =
                            query {
                                (entity: ActivateTestEntity) =>
                                    where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (limit) offset (offset)
                            }
                            
                            runQuery(limit = 2, offset = 1).toList.map(_.stringValue) must beEqualTo(List("a", "b"))
                            runQuery(limit = 10, offset = 2).toList.map(_.stringValue) must beEqualTo(List("b", "c"))
                        }
                    }
                })
        }

        "do not initalize entities unnecessarily" in {
            activateTest(
                (step: StepExecutor) => {
                    if (step.isInstanceOf[MultipleTransactionsWithReinitialize] && !step.ctx.storage.isMemoryStorage) {
                        import step.ctx._
                        val expected = List(null, "a", "b", "c")
                        step {
                            expected.randomize.foreach(v =>
                                newEmptyActivateTestEntity.stringValue = v)
                        }
                        step {
                            val entities =
                                query {
                                    (entity: ActivateTestEntity) =>
                                        where(entity isNotNull) select (entity) orderBy (entity.stringValue) limit (2) offset (1)
                                }.toList
                            entities.map(_.isInitialized).toSet === Set(false)
                        }
                    }
                })
        }
    }

}