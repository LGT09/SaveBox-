package com.example.player

import android.media.audiofx.Equalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EqualizerBandUi(
    val index: Short,
    val centerFreqHz: Int,
    val levelMillibels: Short
)

data class EqualizerState(
    val isSupported: Boolean = false,
    val enabled: Boolean = true,
    val minLevelMb: Short = -1500,
    val maxLevelMb: Short = 1500,
    val bands: List<EqualizerBandUi> = emptyList(),
    val presets: List<String> = listOf("Normal", "Bass Boost", "Treble", "Vocal", "Electronic", "Custom"),
    val selectedPresetIndex: Int = 0
)

object AudioEffectsManager {

    private var equalizer: Equalizer? = null
    private var attachedSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    fun attachToSession(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId) return
        release()
        attachedSessionId = audioSessionId
        try {
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = _state.value.enabled
            }
            equalizer = eq
            val numBands = eq.numberOfBands
            val range = eq.bandLevelRange
            val bands = (0 until numBands).map { i ->
                val idx = i.toShort()
                EqualizerBandUi(
                    index = idx,
                    centerFreqHz = eq.getCenterFreq(idx) / 1000,
                    levelMillibels = eq.getBandLevel(idx)
                )
            }
            val builtInPresets = (0 until eq.numberOfPresets).map { idx ->
                eq.getPresetName(idx.toShort())
            }
            _state.value = _state.value.copy(
                isSupported = true,
                minLevelMb = range[0],
                maxLevelMb = range[1],
                bands = bands,
                presets = if (builtInPresets.isNotEmpty()) builtInPresets + "Custom" else _state.value.presets
            )
        } catch (_: Throwable) {
            _state.value = _state.value.copy(isSupported = false)
        }
    }

    fun setEnabled(enabled: Boolean) {
        runCatching { equalizer?.enabled = enabled }
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val eq = equalizer ?: return
        runCatching {
            val clamped = levelMb.coerceIn(_state.value.minLevelMb, _state.value.maxLevelMb)
            eq.setBandLevel(bandIndex, clamped)
            val updatedBands = _state.value.bands.map {
                if (it.index == bandIndex) it.copy(levelMillibels = clamped) else it
            }
            _state.value = _state.value.copy(
                bands = updatedBands,
                selectedPresetIndex = _state.value.presets.lastIndex
            )
        }
    }

    fun selectPreset(index: Int) {
        val eq = equalizer ?: return
        runCatching {
            if (index < eq.numberOfPresets) {
                eq.usePreset(index.toShort())
                val updatedBands = _state.value.bands.map { band ->
                    band.copy(levelMillibels = eq.getBandLevel(band.index))
                }
                _state.value = _state.value.copy(
                    bands = updatedBands,
                    selectedPresetIndex = index
                )
            } else {
                _state.value = _state.value.copy(selectedPresetIndex = index)
            }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
        attachedSessionId = 0
    }
}
