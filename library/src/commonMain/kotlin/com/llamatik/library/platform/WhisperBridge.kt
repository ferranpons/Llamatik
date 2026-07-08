package com.llamatik.library.platform

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object WhisperBridge {
    fun getModelPath(modelFileName: String): String

    fun initModel(modelPath: String): Boolean
    fun transcribeWav(wavPath: String, language: String? = null, initialPrompt: String? = null): String

    /**
     * Segment-aware transcription. Returns a JSON document exposing per-segment
     * text + start/end timestamps (milliseconds) + the tinydiarize speaker-turn
     * flag, plus the whole-audio detected language code — the data the flat
     * [transcribeWav] discards. Shape:
     *
     * ```json
     * {"language":"de","segments":[
     *     {"text":"Guten Morgen.","t0":0,"t1":1200,"speaker_turn_next":false}
     * ]}
     * ```
     *
     * `t0`/`t1` are milliseconds. `speaker_turn_next` is meaningful only with a
     * tinydiarize (`…-tdrz`) model and is `false` for regular models. Consumed by
     * language-aware / conversation (diarization) transcript views.
     */
    fun transcribeWavSegments(wavPath: String, language: String? = null, initialPrompt: String? = null): String

    fun release()
}
