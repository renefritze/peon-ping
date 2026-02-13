package com.peonping.jetbrains.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.peonping.jetbrains.PeonEngine
import com.peonping.jetbrains.PeonPaths

class CyclePackAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val engine = service<PeonEngine>()
        val next = engine.cyclePack()
        if (next == null) {
            engine.notifyInfo(e.project, "No packs found in ${PeonPaths.packsDir}")
            return
        }
        engine.notifyInfo(e.project, "Switched to pack: $next")
    }
}
