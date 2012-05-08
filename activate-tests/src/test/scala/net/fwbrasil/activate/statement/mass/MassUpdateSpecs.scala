package net.fwbrasil.activate.statement.mass

import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.util.RichList._

@RunWith(classOf[JUnitRunner])
class MassUpdateSpecs extends ActivateTest {

	"Update framework" should {
		"update entity" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						newFullActivateTestEntity
					}
					val newValue = 789
					step {
						update {
							(entity: ActivateTestEntity) => where(entity isNotNull) set (entity.intValue := newValue)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue).toList.distinct must beEqualTo(List(newValue))
					}
				})
		}
		"update entities in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					step {
						(1 to 10).foreach(i => newFullActivateTestEntity)
					}
					val newValue = 789
					step {
						all[ActivateTestEntity].foreach(_.toString) // Just load entities
						update {
							(entity: ActivateTestEntity) => where(entity isNotNull) set (entity.intValue := newValue)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue).toList.distinct must beEqualTo(List(newValue))
					}
				})
		}
		"update entities partially in memory" in {
			activateTest(
				(step: StepExecutor) => {
					import step.ctx._
					val ids =
						step {
							(1 to 10).map(i => newFullActivateTestEntity.id)
						}
					val newValue = 789
					step {
						byId[ActivateTestEntity](ids.last).toString // Just load entity
						update {
							(entity: ActivateTestEntity) => where(entity isNotNull) set (entity.intValue := newValue)
						}.execute
					}
					step {
						all[ActivateTestEntity].map(_.intValue).toList.distinct must beEqualTo(List(newValue))
					}
				})
		}

	}
}