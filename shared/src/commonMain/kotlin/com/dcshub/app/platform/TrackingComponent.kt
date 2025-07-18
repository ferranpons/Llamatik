package com.dcshub.app.platform

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
object TrackingComponent {

    private val EMPTY_TRACKER = EmptyTracker()

    private var tracker: Tracker = EMPTY_TRACKER

    fun setTracker(tracker: Tracker?) {
        if (tracker == null) {
            throw IllegalArgumentException("The LocationPickerTracker instance can't be null.")
        }
        TrackingComponent.tracker = tracker
    }

    fun getTracker(): Tracker {
        return tracker
    }

    fun reset() {
        tracker = EMPTY_TRACKER
    }

    class EmptyTracker : Tracker {
        override fun onEventTracked(event: TrackEvents) {}
    }
}

enum class TrackEvents(val eventName: String) {
    APP_STARTED("App Started"),
    LOGIN_SUCCESSFUL("Login Successful"),
    LOGIN_ERROR("Login Error"),
    SIGN_UP_SUCCESSFUL("Sign Up Successful"),
    SIGN_UP_ERROR("Sign Up Error"),
    PET_UPLOAD_SUCCESSFUL("Pet Upload Successful"),
    PET_UPLOAD_ERROR("Pet Upload Error"),
    REQUEST_ERROR("Request Error")
}

interface Tracker {
    fun onEventTracked(event: TrackEvents)
}
