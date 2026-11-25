package com.neonsong.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.launch

class NowPlayingWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val app = context.applicationContext
        WidgetUpdater.scope.launch {
            try {
                WidgetUpdater.updateWidgetIds(app, manager, ids, isObsidian = false)
                val snapshot = WidgetSnapshot.read(app)
                if (snapshot.hasSession && snapshot.isPlaying) {
                    WidgetUpdater.startWaveAnimation(app)
                }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
        val pending = goAsync()
        val app = context.applicationContext
        WidgetUpdater.scope.launch {
            try {
                WidgetUpdater.updateWidgetIds(app, manager, intArrayOf(appWidgetId), isObsidian = false)
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetActions.ACTION_TOGGLE,
            WidgetActions.ACTION_NEXT,
            WidgetActions.ACTION_PREV,
            -> {
                val action = intent.action ?: return
                val pending = goAsync()
                val app = context.applicationContext
                WidgetUpdater.scope.launch {
                    try {
                        when (action) {
                            WidgetActions.ACTION_TOGGLE -> WidgetActions.performToggle(app)
                            WidgetActions.ACTION_NEXT -> WidgetActions.performSkip(app, next = true)
                            WidgetActions.ACTION_PREV -> WidgetActions.performSkip(app, next = false)
                        }
                    } finally {
                        runCatching { pending.finish() }
                    }
                }
                return
            }
            else -> super.onReceive(context, intent)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        val pending = goAsync()
        val app = context.applicationContext
        WidgetUpdater.scope.launch {
            try {
                runCatching { WidgetUpdater.sync(app) }
            } finally {
                runCatching { pending.finish() }
            }
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetUpdater.stopIfNoWidgets(context.applicationContext)
    }
}
