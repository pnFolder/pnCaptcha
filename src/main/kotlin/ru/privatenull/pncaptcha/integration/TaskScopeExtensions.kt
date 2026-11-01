package ru.privatenull.pncaptcha.integration

import org.pnfolder.pnlibrary.api.tasks.TaskHandle
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSpec
import java.time.Duration

/** Запускает [action] один раз через [delay]. */
fun TaskScope.later(name: String, delay: Duration, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name(name)
        .delay(delay)
        .action { action.run() }
        .build(),
)

/** Повторяет [action] с периодом [period] без задержки перед первым запуском. */
fun TaskScope.repeat(name: String, period: Duration, action: Runnable): TaskHandle = schedule(
    TaskSpec.builder()
        .name(name)
        .interval(period)
        .action { action.run() }
        .build(),
)
