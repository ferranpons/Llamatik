package com.dcshub.app.platform

import co.touchlab.kermit.Logger

class LlamatikEventTracker : Tracker {
    override fun onEventTracked(event: TrackEvents) {
        Logger.d { "Event: ${event.name}" }
    }
}
