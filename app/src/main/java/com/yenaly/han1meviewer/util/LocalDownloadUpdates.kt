package com.yenaly.han1meviewer.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

/** Serialize local maintenance across both entry points and refresh metadata after partial changes. */
object LocalDownloadUpdates {
    val operationMutex = Mutex()
    private val revision = MutableStateFlow(0)
    val changes = revision.asStateFlow()

    fun notifyChanged() {
        revision.update { it + 1 }
    }
}
