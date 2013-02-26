package net.fwbrasil.activate.storage

import java.util.Date
import net.fwbrasil.activate.util.RichList.toRichList
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.storage.memory.TransientMemoryStorageSet
import java.sql.Connection
import com.mongodb.DB
import org.prevayler.Prevayler
import net.fwbrasil.activate.ActivateTestContext
import org.prevayler.{ Transaction => PrevaylerTransaction }
import net.fwbrasil.activate.storage.prevayler.PrevaylerStorageSystem
import net.fwbrasil.activate.polyglotContext

@RunWith(classOf[JUnitRunner])
class StorageDirectAccessSpecs extends ActivateTest {

    override def executors(ctx: ActivateTestContext) =
        super.executors(ctx).filterNot(_.isInstanceOf[OneTransaction])

    override def contexts =
        super.contexts.filter(_ != polyglotContext)

    "Activate" should {
        "provide direct access to the storage" in {
            activateTest(
                (step: StepExecutor) => {
                    import step.ctx._
                    val id =
                        step {
                            newEmptyActivateTestEntity.id
                        }
                    step {
                        storage.directAccess match {
                            case memorySet: TransientMemoryStorageSet =>
                                memorySet.filter(_.isInstanceOf[ActivateTestEntity]).onlyOne.id must beEqualTo(id)
                            case jdbcConnection: Connection =>
                                try {
                                    val stmt = jdbcConnection.createStatement
                                    val result = stmt.executeQuery("SELECT ID FROM ActivateTestEntity")
                                    result.next must beTrue
                                    result.getString(1) must beEqualTo(id)
                                    result.next must beFalse
                                } finally {
                                    jdbcConnection.rollback
                                    jdbcConnection.close
                                }
                            case mongoDB: DB =>
                                val cursor = mongoDB.getCollection("ActivateTestEntity").find
                                cursor.next.get("_id") must beEqualTo(id)
                                cursor.hasNext must beFalse
                            case prevayler: Prevayler[_] =>
                                prevayler.asInstanceOf[Prevayler[PrevaylerStorageSystem]]
                                    .execute(new PrevaylerTransaction[PrevaylerStorageSystem] {
                                        override def executeOn(system: PrevaylerStorageSystem, date: Date) = {
                                            val sys = system.asInstanceOf[PrevaylerStorageSystem]
                                            sys.values.filter(_.isInstanceOf[ActivateTestEntity]).onlyOne.id must beEqualTo(id)
                                        }
                                    })
                        }
                    }
                })
        }
    }

}