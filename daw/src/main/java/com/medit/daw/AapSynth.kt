package com.medit.daw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import org.androidaudioplugin.PortInformation
import org.androidaudioplugin.PluginInformation
import org.androidaudioplugin.hosting.AudioPluginInstance

/**
 * Adapts an AAP (AndroidAudioPlugin) [AudioPluginInstance] to the [TrackSynth]
 * interface. Each track gets its own [AapSynth] with its own [AudioTrack];
 * Android mixes the outputs of all tracks.
 *
 * MIDI note events are converted to UMP MIDI packets and delivered to the
 * plugin via [AudioPluginInstance.addEventUmpInput]. The plugin renders audio
 * on a dedicated render thread and the output is written to the track's
 * [AudioTrack].
 */
class AapSynth(
    private val plugin: AudioPluginInstance,
    private val pluginInfo: PluginInformation,
    private val outputSampleRate: Int = 44100,
) : TrackSynth {

    /**
     * A MIDI event with its precise sample position within the render chunk.
     * Allows the render loop to schedule note on/off at the exact sample
     * offset within the 256-frame chunk.
     */
    private sealed interface MidiEvent {
        data class NoteOn(val sampleOffset: Int, val pitch: Int, val velocity: Int, val channel: Int) : MidiEvent
        data class NoteOff(val sampleOffset: Int, val pitch: Int, val channel: Int) : MidiEvent
        data class Sustain(val sampleOffset: Int, val on: Boolean) : MidiEvent
    }

    private sealed interface Command {
        data class NoteOn(val sampleOffset: Int, val pitch: Int, val velocity: Int, val channel: Int) : Command
        data class NoteOff(val sampleOffset: Int, val pitch: Int, val channel: Int) : Command
        data class Sustain(val sampleOffset: Int, val on: Boolean) : Command
        data class Volume(val sampleOffset: Int, val value: Float) : Command
        data class AllNotesOff(val sampleOffset: Int, val fast: Boolean) : Command
        data object Shutdown : Command
    }

    private val commands = ConcurrentLinkedQueue<Command>()
    private val audioTrack: AudioTrack
    private val renderThread: Thread
    @Volatile private var running = true
    @Volatile private var sustainOn = false
    @Volatile private var masterVolume = 1f

    private val chunkFrames = 256
    private val floatBuffer = FloatArray(chunkFrames * 2) // stereo
    private val shortBuffer = ShortArray(chunkFrames * 2)
    private val umpBuffer = ByteArray(16) // one UMP event
    /** The stereo audio output port index, or -1 if none found. */
    private val audioOutputPortIndex: Int
    /** Minimum buffer size in bytes required by the audio output port. */
    private val audioOutputPortMinBytes: Int
    /** AudioTrack buffer size in bytes, captured at initialization. */
    private val audioTrackBufferSizeBytes: Int
    private val portBuffer: ByteBuffer

    init {
        // Discover the stereo audio output port from the plugin's port list.
        var foundOutputPort = -1
        var foundMinBytes = 0
        val portCount = plugin.getPortCount()
        for (i in 0 until portCount) {
            val port = plugin.getPort(i)
            if (port.direction == PortInformation.PORT_DIRECTION_OUTPUT &&
                port.content == PortInformation.PORT_CONTENT_TYPE_AUDIO) {
                foundOutputPort = i
                foundMinBytes = port.minimumSizeInBytes
                break
            }
        }
        audioOutputPortIndex = foundOutputPort
        audioOutputPortMinBytes = foundMinBytes

        // Allocate the port read buffer based on the port's minimum size requirement.
        // AAP requires the buffer to be at least this size.
        val portBufSize = maxOf(foundMinBytes, chunkFrames * 8) // 8 bytes per stereo frame (2 floats)
        portBuffer = ByteBuffer.allocateDirect(portBufSize).order(ByteOrder.LITTLE_ENDIAN)

        // Prepare the plugin with sample rate and block size
        plugin.prepare(outputSampleRate, chunkFrames)
        plugin.activate()

        val minBuffer = AudioTrack.getMinBufferSize(
            outputSampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) throw IllegalStateException("オーディオバッファを作成できません ($minBuffer)")
        val bufferBytes = maxOf(minBuffer * 2, chunkFrames * 4 * 3)
        audioTrackBufferSizeBytes = bufferBytes
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(outputSampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        if (audioTrack.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack.release()
            throw IllegalStateException("AudioTrackを初期化できません")
        }
        audioTrack.play()

        renderThread = Thread({ renderLoop() }, "AapSynth-${pluginInfo.displayName}").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun renderLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        while (running) {
            // Drain pending commands into per-chunk event lists
            val noteOnEvents = mutableListOf<MidiEvent.NoteOn>()
            val noteOffEvents = mutableListOf<MidiEvent.NoteOff>()
            val sustainEvents = mutableListOf<MidiEvent.Sustain>()
            var volumeChanged = false
            var newMasterVolume = masterVolume
            var allNotesOff = false

            var cmd: Command? = commands.poll()
            while (cmd != null) {
                when (cmd) {
                    is Command.NoteOn -> noteOnEvents += MidiEvent.NoteOn(cmd.sampleOffset, cmd.pitch, cmd.velocity, cmd.channel)
                    is Command.NoteOff -> noteOffEvents += MidiEvent.NoteOff(cmd.sampleOffset, cmd.pitch, cmd.channel)
                    is Command.Sustain -> sustainEvents += MidiEvent.Sustain(cmd.sampleOffset, cmd.on)
                    is Command.Volume -> {
                        volumeChanged = true
                        newMasterVolume = cmd.value.coerceIn(0f, 4f)
                    }
                    is Command.AllNotesOff -> allNotesOff = true
                    is Command.Shutdown -> running = false
                }
                cmd = commands.poll()
            }
            if (!running) break

            // Send MIDI events to the plugin at their sample offsets within the chunk.
            // AAP's addEventUmpInput is called per event; the plugin handles the
            // timestamp internally via the process() call boundary.
            for (e in noteOnEvents) {
                sendNoteOn(e.sampleOffset, e.pitch, e.velocity, e.channel)
            }
            for (e in noteOffEvents) {
                sendNoteOff(e.sampleOffset, e.pitch, e.channel)
            }
            for (e in sustainEvents) {
                sendSustain(e.sampleOffset, e.on)
            }
            if (allNotesOff) {
                sendAllNotesOff()
            }
            if (volumeChanged) {
                masterVolume = newMasterVolume
            }

            // Render one chunk of audio through the plugin
            plugin.process(chunkFrames, (System.nanoTime() / 1_000_000L).toLong())

            // Read output from the discovered stereo audio output port
            if (audioOutputPortIndex >= 0) {
                plugin.getPortBuffer(audioOutputPortIndex, portBuffer, chunkFrames)
                portBuffer.rewind()
                val framesRead = portBuffer.remaining() / 8 // 8 bytes per stereo frame
                for (i in 0 until framesRead * 2) {
                    val x = portBuffer.getFloat() * masterVolume
                    val clamped = x.coerceIn(-1f, 1f)
                    shortBuffer[i] = (clamped * 32767f).toInt().toShort()
                }
                audioTrack.write(shortBuffer, 0, framesRead * 2)
            }

            // Sleep briefly to avoid busy-waiting
            Thread.sleep(4)
        }
    }

    private fun sendNoteOn(sampleOffset: Int, pitch: Int, velocity: Int, channel: Int) {
        // UMP MIDI: 0x90 | channel, pitch, velocity
        umpBuffer[0] = (0x90 or (channel and 0x0F)).toByte()
        umpBuffer[1] = pitch.coerceIn(0, 127).toByte()
        umpBuffer[2] = velocity.coerceIn(1, 127).toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    private fun sendNoteOff(sampleOffset: Int, pitch: Int, channel: Int) {
        umpBuffer[0] = (0x80 or (channel and 0x0F)).toByte()
        umpBuffer[1] = pitch.coerceIn(0, 127).toByte()
        umpBuffer[2] = 0.toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    private fun sendSustain(sampleOffset: Int, on: Boolean) {
        sustainOn = on
        umpBuffer[0] = (0xB0 or 0x00).toByte() // CC0 on channel 0
        umpBuffer[1] = 64.toByte() // Sustain CC
        umpBuffer[2] = if (on) 127.toByte() else 0.toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    private fun sendAllNotesOff() {
        umpBuffer[0] = (0xB0 or 0x00).toByte() // CC0 on channel 0
        umpBuffer[1] = 123.toByte() // All Notes Off CC
        umpBuffer[2] = 0.toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    override fun noteOn(pitch: Int, velocity: Int, channel: Int, sampleOffset: Int) {
        if (!running) return
        commands.add(Command.NoteOn(sampleOffset.coerceIn(0, chunkFrames - 1), pitch.coerceIn(0, 127), velocity.coerceIn(1, 127), channel and 0x0F))
    }

    override fun noteOff(pitch: Int, channel: Int, sampleOffset: Int) {
        if (!running) return
        commands.add(Command.NoteOff(sampleOffset.coerceIn(0, chunkFrames - 1), pitch.coerceIn(0, 127), channel and 0x0F))
    }

    override fun setSustain(on: Boolean, sampleOffset: Int) {
        if (!running) return
        commands.add(Command.Sustain(sampleOffset.coerceIn(0, chunkFrames - 1), on))
    }

    override fun allNotesOff(fast: Boolean, sampleOffset: Int) {
        commands.add(Command.AllNotesOff(sampleOffset.coerceIn(0, chunkFrames - 1), fast))
    }

    override fun setMasterVolume(volume: Float, sampleOffset: Int) {
        commands.add(Command.Volume(sampleOffset.coerceIn(0, chunkFrames - 1), volume.coerceIn(0f, 4f)))
    }

    override fun close() {
        if (!running) return
        running = false
        commands.add(Command.Shutdown)
        renderThread.interrupt()
        try {
            renderThread.join(3000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        // Deactivate and destroy the plugin only after the render thread has stopped
        // to avoid concurrent access to native resources.
        plugin.deactivate()
        plugin.destroy()
        audioTrack.stop()
        audioTrack.release()
    }

    /** Returns latency information for debugging and measurement. */
    override fun getLatencyInfo(): TrackSynth.LatencyInfo? {
        val audioTrackBufferSize = audioTrackBufferSizeBytes
        val audioTrackLatencyMs = (audioTrackBufferSize.toDouble() / (outputSampleRate * 2 * 2) * 1000.0).toFloat()
        // 2 bytes per sample, 2 channels
        val chunkLatencyMs = (chunkFrames.toDouble() / outputSampleRate * 1000.0).toFloat()
        return TrackSynth.LatencyInfo(
            audioTrackBufferSizeBytes = audioTrackBufferSize,
            audioTrackLatencyMs = audioTrackLatencyMs,
            chunkFrames = chunkFrames,
            chunkLatencyMs = chunkLatencyMs,
            portMinBytes = audioOutputPortMinBytes,
            estimatedTotalLatencyMs = audioTrackLatencyMs + chunkLatencyMs,
        )
    }

    companion object {
        const val CHANNEL_COUNT = 2
    }
}
