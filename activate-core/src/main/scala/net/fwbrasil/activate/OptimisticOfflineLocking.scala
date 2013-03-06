package net.fwbrasil.activate

import System.getProperty
import net.fwbrasil.activate.entity.Var

object OptimisticOfflineLocking {

    private val propertiesPrefix =
        "activate.coordinator.optimisticOfflineLocking."

    val isEnabled =
        getProperty(propertiesPrefix + "enable", "false") == "true"

    val validateReads =
        getProperty(propertiesPrefix + "validateReads", "false") == "true"

    if (validateReads)
        require(isEnabled, "Cannot validate reads if optimistic offline locking is disabled")

    val versionVarName =
        "version"

    def isVersionVar(ref: Var[Any]) =
        ref.name == versionVarName

}