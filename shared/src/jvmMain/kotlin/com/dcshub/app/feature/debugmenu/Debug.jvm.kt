package com.dcshub.app.feature.debugmenu

class JvmDebug : Debug {
    override val isDebug: Boolean = true
}

actual fun getDebug(): Debug = JvmDebug()
