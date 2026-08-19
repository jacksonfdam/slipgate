package com.jacksonfdam.slipgate.host.audio.synth

/** Every sound the interface makes. There are no audio files; each cue is a recipe. */
public enum class InterfaceCue {
    /** 900 ms rising filtered-noise sweep resolving to a two-oscillator chord. */
    Boot,

    /** 28 ms blip: square through a fast-decaying lowpass. */
    Navigate,

    /** Two-note rising interval, 140 ms. */
    Confirm,

    /** The confirm interval inverted. */
    Back,

    /** Detuned minor second, short, no reverb. */
    Blocked,

    /** Filtered noise sweep; pitch tracks scroll direction. */
    FocusChange,

    /** 900 ms swell, pitch and filter ramping with the launch transition. */
    Launch,
}

/** The cue table, built once; triggering never constructs programs. */
internal fun cuePrograms(): Map<InterfaceCue, List<VoiceProgram>> =
    mapOf(
        InterfaceCue.Boot to bootPrograms(),
        InterfaceCue.Navigate to navigatePrograms(),
        InterfaceCue.Confirm to interval(firstHz = 587.33f, secondHz = 783.99f),
        InterfaceCue.Back to interval(firstHz = 783.99f, secondHz = 587.33f),
        InterfaceCue.Blocked to
            listOf(
                blockedVoice(hz = 220f, pan = -0.15f),
                blockedVoice(hz = 233.08f, pan = 0.15f),
            ),
        InterfaceCue.FocusChange to focusPrograms(),
        InterfaceCue.Launch to launchPrograms(),
    )

private fun bootPrograms(): List<VoiceProgram> =
    listOf(
        VoiceProgram(
            waveform = Waveform.Noise,
            startHz = 0f,
            endHz = 0f,
            amplitude = 0.32f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 180f,
            cutoffEndHz = 5200f,
            damping = 0.8f,
            attackMs = 40f,
            decayMs = 300f,
            sustain = 0.5f,
            releaseMs = 320f,
            durationMs = 780f,
            reverbSend = 0.25f,
        ),
        VoiceProgram(
            waveform = Waveform.Saw,
            startHz = 110f,
            endHz = 110f,
            amplitude = 0.2f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 900f,
            cutoffEndHz = 2600f,
            damping = 1.1f,
            attackMs = 120f,
            decayMs = 160f,
            sustain = 0.7f,
            releaseMs = 260f,
            durationMs = 520f,
            delayMs = 380f,
            reverbSend = 0.35f,
            pan = -0.2f,
        ),
        VoiceProgram(
            waveform = Waveform.Saw,
            startHz = 165f,
            endHz = 165f,
            amplitude = 0.16f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 900f,
            cutoffEndHz = 3200f,
            damping = 1.1f,
            attackMs = 120f,
            decayMs = 160f,
            sustain = 0.7f,
            releaseMs = 260f,
            durationMs = 520f,
            delayMs = 380f,
            reverbSend = 0.35f,
            pan = 0.2f,
        ),
    )

private fun navigatePrograms(): List<VoiceProgram> =
    listOf(
        VoiceProgram(
            waveform = Waveform.Square,
            startHz = 1180f,
            endHz = 1180f,
            amplitude = 0.22f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 4200f,
            cutoffEndHz = 320f,
            damping = 1.2f,
            attackMs = 1f,
            decayMs = 20f,
            sustain = 0f,
            releaseMs = 6f,
            durationMs = 28f,
        ),
    )

private fun focusPrograms(): List<VoiceProgram> =
    listOf(
        VoiceProgram(
            waveform = Waveform.Noise,
            startHz = 0f,
            endHz = 0f,
            amplitude = 0.18f,
            filterMode = FilterMode.Band,
            cutoffStartHz = 700f,
            cutoffEndHz = 2600f,
            damping = 0.35f,
            attackMs = 6f,
            decayMs = 40f,
            sustain = 0.3f,
            releaseMs = 40f,
            durationMs = 90f,
            tracksDirection = true,
        ),
    )

private fun launchPrograms(): List<VoiceProgram> =
    listOf(
        VoiceProgram(
            waveform = Waveform.Saw,
            startHz = 110f,
            endHz = 440f,
            amplitude = 0.26f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 320f,
            cutoffEndHz = 5600f,
            damping = 0.9f,
            attackMs = 60f,
            decayMs = 200f,
            sustain = 0.85f,
            releaseMs = 180f,
            durationMs = 900f,
            reverbSend = 0.3f,
        ),
        VoiceProgram(
            waveform = Waveform.PinkNoise,
            startHz = 0f,
            endHz = 0f,
            amplitude = 0.2f,
            filterMode = FilterMode.Low,
            cutoffStartHz = 500f,
            cutoffEndHz = 7800f,
            damping = 0.8f,
            attackMs = 300f,
            decayMs = 300f,
            sustain = 0.9f,
            releaseMs = 200f,
            durationMs = 900f,
            reverbSend = 0.2f,
        ),
    )

private fun interval(
    firstHz: Float,
    secondHz: Float,
): List<VoiceProgram> =
    listOf(
        intervalNote(hz = firstHz, delayMs = 0f),
        intervalNote(hz = secondHz, delayMs = 70f),
    )

private fun intervalNote(
    hz: Float,
    delayMs: Float,
): VoiceProgram =
    VoiceProgram(
        waveform = Waveform.Sine,
        startHz = hz,
        endHz = hz,
        amplitude = 0.24f,
        attackMs = 4f,
        decayMs = 30f,
        sustain = 0.5f,
        releaseMs = 30f,
        durationMs = 70f,
        delayMs = delayMs,
        reverbSend = 0.12f,
    )

private fun blockedVoice(
    hz: Float,
    pan: Float,
): VoiceProgram =
    VoiceProgram(
        waveform = Waveform.Square,
        startHz = hz,
        endHz = hz,
        amplitude = 0.14f,
        filterMode = FilterMode.Low,
        cutoffStartHz = 1400f,
        cutoffEndHz = 900f,
        damping = 1.2f,
        attackMs = 2f,
        decayMs = 60f,
        sustain = 0.2f,
        releaseMs = 40f,
        durationMs = 130f,
        pan = pan,
    )
