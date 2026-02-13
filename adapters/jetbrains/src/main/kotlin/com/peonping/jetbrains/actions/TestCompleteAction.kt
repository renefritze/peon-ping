package com.peonping.jetbrains.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.peonping.jetbrains.CespCategory
import com.peonping.jetbrains.PeonEngine

class TestCompleteAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val engine = service<PeonEngine>()
        val projectName = e.project?.name ?: "project"
        engine.emit(CespCategory.TASK_COMPLETE, projectName)
        engine.notifyInfo(e.project, "Played task.complete sound")
    }
}

