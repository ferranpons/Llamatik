package com.llamatik.app.feature.debugmenu

interface Debug {
    val isDebug: Boolean
}

expect fun getDebug(): Debug
