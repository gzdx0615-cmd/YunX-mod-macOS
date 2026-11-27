package com.yunx.app.util

import java.awt.EventQueue
import java.awt.Taskbar

/** macOS exposes ongoing progress in the Dock, without repeatedly posting notifications. */
internal object MacDownloadProgress {
    private var lastUpdate = 0L
    fun update(done: Long, total: Long, title: String, speed: String) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 1000) return
        lastUpdate = now
        val percent = if (total > 0) (done.toDouble() / total * 100).toInt().coerceIn(0, 100) else 0
        TrayManager.setProgressTooltip("$title · $percent% · $speed")
        EventQueue.invokeLater {
            runCatching {
                if (!Taskbar.isTaskbarSupported()) return@runCatching
                val dock = Taskbar.getTaskbar()
                if (dock.isSupported(Taskbar.Feature.PROGRESS_VALUE)) dock.setProgressValue(percent)
                if (dock.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) dock.setIconBadge(if (total > 0) "$percent%" else "…")
            }
        }
    }
    fun clear() {
        lastUpdate = 0
        TrayManager.setProgressTooltip(null)
        EventQueue.invokeLater {
            runCatching {
                if (!Taskbar.isTaskbarSupported()) return@runCatching
                val dock = Taskbar.getTaskbar()
                if (dock.isSupported(Taskbar.Feature.PROGRESS_VALUE)) dock.setProgressValue(-1)
                if (dock.isSupported(Taskbar.Feature.ICON_BADGE_TEXT)) dock.setIconBadge(null)
            }
        }
    }
}
