package net.fwbrasil.activate.statement.query

import net.fwbrasil.activate.util.RichList._
import net.fwbrasil.activate.ActivateTest
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.derbyContext
import net.fwbrasil.activate.h2Context
import net.fwbrasil.activate.hsqldbContext

@RunWith(classOf[JUnitRunner])
class CachedQuerySpecs extends ActivateTest {
    
    override def contexts = super.contexts.filter(c => c != derbyContext && c != h2Context && c != hsqldbContext)

    "Cached queries" should {

        "return correct values" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val entityId =
                        step {
                            newEmptyActivateTestEntity
                            newFullActivateTestEntity.id
                        }
                    step {
                        val cached =
                            cachedQuery {
                                (e: ActivateTestEntity) => where(e.intValue :== fullIntValue)
                            }
                        val normal =
                            query {
                                (e: ActivateTestEntity) => where(e.intValue :== fullIntValue) select (e)
                            }
                        cached.toList === normal
                    }
                })
        }

        "be faster after the initial load" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    if (!step.ctx.storage.isMemoryStorage && step.isInstanceOf[MultipleTransactionsWithReinitialize]) {
                        val entityId =
                            step {
                                for (i <- 0 until 100)
                                    newEmptyActivateTestEntity
                            }
                        def runCached =
                            cachedQuery {
                                (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue)
                            }

                        def runNormal =
                            query {
                                (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue) select (e)
                            }
                        step {
                            runNormal // just to load entities
                            val a = timeToRun(runCached)
                            val b = timeToRun(runCached)
                            b < a must beTrue
                        }
                    }
                })
        }

        "be updated for new/modified/deleted entities" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    step {
                        newEmptyActivateTestEntity
                    }
                    def runCached =
                        cachedQuery {
                            (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue)
                        }.toSet
                    def runCachedAndVerify =
                        runCached ===
                            query {
                                (e: ActivateTestEntity) => where(e.intValue :== emptyIntValue) select (e)
                            }.toSet
                    step {
                        runCachedAndVerify
                    }
                    step {
                        newEmptyActivateTestEntity
                    }
                    step {
                        runCachedAndVerify
                    }
                    step {
                        newEmptyActivateTestEntity.intValue = fullIntValue
                    }
                    step {
                        runCachedAndVerify
                    }
                    step {
                        runCached.head.intValue = fullIntValue
                    }
                    step {
                        runCachedAndVerify
                    }
                    step {
                        all[ActivateTestEntity].foreach(_.delete)
                    }
                    step {
                        runCachedAndVerify
                    }
                })
        }
    }

    def timeToRun(f: => Unit) = {
        val start = System.currentTimeMillis
        f
        System.currentTimeMillis - start
    }

}