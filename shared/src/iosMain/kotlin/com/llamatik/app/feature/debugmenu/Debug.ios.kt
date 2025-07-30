@file:OptIn(ExperimentalNativeApi::class)

package com.llamatik.app.feature.debugmenu

import kotlin.experimental.ExperimentalNativeApi

class IOSDebug : Debug {
    override val isDebug: Boolean
        get() = Platform.isDebugBinary
}
