package net.fwbrasil.activate.multipleVms

import net.fwbrasil.activate.StoppableActivateContext
import net.fwbrasil.activate.postgresqlContext
import net.fwbrasil.activate.entity.Entity
import net.fwbrasil.activate.migration.ManualMigration

trait MultiVMContext extends StoppableActivateContext {

    override val milisToWaitBeforeRetry = 1

    class IntEntity extends Entity {
        var intValue = 0
    }

    object versionMigration extends ManualMigration {
        def up =
            table[IntEntity]
                .addColumn(_.column[Long]("version"))
                .ifNotExists
    }

    val storage = postgresqlContext.storage
    
    versionMigration.execute
    
    def run[A](f: => A) = {
        start
        try
            transactional(f)
        finally
            stop
    }

}

object ctx1 extends MultiVMContext
object ctx2 extends MultiVMContext