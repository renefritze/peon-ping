package com.peonping.jetbrains

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class PeonStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        val engine = service<PeonEngine>()
        engine.emitSessionStart(project)

        project.messageBus.connect(project).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    engine.handleIdeNotification(project, notification)
                }
            },
        )
    }
}

