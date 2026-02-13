package com.peonping.jetbrains.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.peonping.jetbrains.PeonEngine

class TogglePauseAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val engine = service<PeonEngine>()
        val paused = engine.togglePause()
        val status = if (paused) "Sounds paused" else "Sounds resumed"
        engine.notifyInfo(e.project, status)
    }
}
