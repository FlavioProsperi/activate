package net.fwbrasil.activate.migration

import net.fwbrasil.activate.storage.relational.PooledJdbcRelationalStorage
import net.fwbrasil.activate.ActivateContext
import net.fwbrasil.activate.ActivateTest
import net.fwbrasil.activate.ActivateTestContext
import net.fwbrasil.activate.runningFlag

class MigrationTest extends ActivateTest {

	@ManualMigration
	abstract class TestMigration(override implicit val context: ActivateTestContext) extends Migration {

		Migration.migrationsCache.put(context, Migration.migrationsCache.getOrElse(context, List()) ++ List(this))

		def timestamp = Migration.migrationsCache(context).indexOf(this)
		override val developers = List("tester")

		def validateSchemaError(f: => Unit) =
			if (context.storage.hasStaticScheme)
				context.transactional {
					f
				} must throwA[Exception]

		def validateUp: Unit = {}
		def validateDown: Unit = {}
	}

	def migrationTest(registers: ((ActivateTestContext) => TestMigration)*) =
		runningFlag.synchronized {
			for (ctx <- contexts) {
				import ctx._
				ctx.start
					def clear = {
						Migration.storageVersion(ctx) // Crate StorageVersion if not exists
						ctx.transactional {
							ctx.delete {
								(s: StorageVersion) => where(s isNotNull)
							}
						}
						if (ctx.storage.isInstanceOf[PooledJdbcRelationalStorage])
							ctx.storage.asInstanceOf[PooledJdbcRelationalStorage].dataSource.hardReset
						ActivateContext.clearContextCaches
					}
				clear

				new TestMigration()(ctx) {
					def up = {
						removeReferencesForAllEntities
							.ifExists
						removeAllEntitiesTables
							.ifExists
							.cascade
					}

				}
				val migrations = registers.map(_(ctx))
				try {
					for (migration <- migrations) {
						Migration.updateTo(ctx, migration.timestamp)
						migration.validateUp
					}
					for (migration <- migrations.reverse) {
						Migration.revertTo(ctx, migration.timestamp - 1)
						migration.validateDown
					}
				} catch {
					case e =>
						e.printStackTrace
						throw e
				} finally {
					clear
					stop
				}
			}
			true must beTrue
		}
}