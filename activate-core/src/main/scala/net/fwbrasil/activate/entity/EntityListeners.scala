package net.fwbrasil.activate.entity

import language.implicitConversions
import net.fwbrasil.radon.ref.RefListener
import net.fwbrasil.activate.statement.StatementMocks
import net.fwbrasil.radon.ref.Ref
import scala.collection.mutable.ListBuffer

trait EntityListeners {
    this: Entity =>

    @transient
    private lazy val listeners = entityMetadata.listenerMethods.map(_.invoke(this))

    protected class On(val vars: List[Var[Any]]) {
        def change(f: => Unit): RefListener[_] = {
            val listener = new RefListener[Any] {
                override def notifyPut(ref: Ref[Any], obj: Option[Any]) =
                    f
            }
            vars.foreach(_.addWeakListener(listener))
            listener
        }
    }
    
    private[activate] def initializeListeners =
        listeners

    protected def onAny =
        new On(vars)

    protected def on(functions: (EntityListeners.this.type => Any)*) =
        new On(functions.map(StatementMocks.funcToVarName(_)).map(varNamed).toList)

}