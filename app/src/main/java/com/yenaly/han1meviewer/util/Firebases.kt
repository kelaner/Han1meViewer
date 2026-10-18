package com.yenaly.han1meviewer.util

import android.app.Activity
import android.util.Log
import androidx.fragment.app.Fragment
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.yenaly.yenaly_libs.utils.applicationContext
import java.util.concurrent.atomic.AtomicBoolean
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

private val missingFirebaseLogged = AtomicBoolean(false)

fun isFirebaseAvailable(): Boolean {
    val available = FirebaseApp.getApps(applicationContext).any { it.name == FirebaseApp.DEFAULT_APP_NAME }
    if (!available && missingFirebaseLogged.compareAndSet(false, true)) {
        Log.w("Firebase", "缺少 Firebase 配置，跳过统计、崩溃上报和云端公告；GitHub 更新继续可用")
    }
    return available
}

fun Activity.logScreenViewEvent(fragment: Fragment) {
    logScreenViewEvent(fragment.javaClass.simpleName)
}

fun Activity.logScreenViewEvent(screenClassName: String) {
    if (!isFirebaseAvailable()) return
    Firebase.analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
        // example: MainActivity-HomeRouteScreen
        val screenName = this@logScreenViewEvent.javaClass.simpleName +
            "-" + screenClassName
        Log.d("logScreenViewEvent", "screenName: $screenName")
        param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        param(FirebaseAnalytics.Param.SCREEN_CLASS, screenClassName)
    }
}
