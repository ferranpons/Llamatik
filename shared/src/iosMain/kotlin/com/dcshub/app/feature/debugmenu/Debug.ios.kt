@file:OptIn(ExperimentalNativeApi::class)

package com.dcshub.app.feature.debugmenu

import kotlin.experimental.ExperimentalNativeApi

class IOSDebug : Debug {
    override val isDebug: Boolean
        get() = Platform.isDebugBinary
}

actual fun getDebug(): Debug = IOSDebug()
