package com.medit.daw

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
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

    private sealed interface Command {
        data class NoteOn(val pitch: Int, val velocity: Int, val channel: Int) : Command
        data class NoteOff(val pitch: Int, val channel: Int) : Command
        data class Sustain(val on: Boolean) : Command
        data class Volume(val value: Float) : Command
        data class AllNotesOff(val fast: Boolean) : Command
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
    private val portBuffer = ByteBuffer.allocateDirect(chunkFrames * 8).order(ByteOrder.LITTLE_ENDIAN)

    init {
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
            // Drain pending commands
            var cmd: Command? = commands.poll()
            while (cmd != null) {
                when (cmd) {
                    is Command.NoteOn -> sendNoteOn(cmd.pitch, cmd.velocity, cmd.channel)
                    is Command.NoteOff -> sendNoteOff(cmd.pitch, cmd.channel)
                    is Command.Sustain -> sendSustain(cmd.on)
                    is Command.Volume -> masterVolume = cmd.value.coerceIn(0f, 4f)
                    is Command.AllNotesOff -> {
                        // Send all notes off via UMP MIDI
                        umpBuffer[0] = (0xB0 or 0x00).toByte() // CC0 on channel 0
                        umpBuffer[1] = 123.toByte() // All Notes Off CC
                        umpBuffer[2] = 0.toByte()
                        umpBuffer[3] = 0
                        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
                        plugin.addEventUmpInput(bb, 1)
                    }
                    is Command.Shutdown -> running = false
                }
                cmd = commands.poll()
            }
            if (!running) break

            // Render one chunk
            plugin.process(chunkFrames, (System.nanoTime() / 1_000_000L).toLong())

            // Get output from port 0 (stereo output)
            val portCount = plugin.getPortCount()
            if (portCount > 0) {
                val outPort = plugin.getPort(0)
                // Assuming port 0 is stereo output
                plugin.getPortBuffer(0, portBuffer, chunkFrames)
                portBuffer.rewind()
                for (i in 0 until chunkFrames * 2) {
                    val x = portBuffer.getFloat() * masterVolume
                    val clamped = x.coerceIn(-1f, 1f)
                    shortBuffer[i] = (clamped * 32767f).toInt().toShort()
                }
                audioTrack.write(shortBuffer, 0, chunkFrames * 2)
            }

            // Sleep briefly to avoid busy-waiting
            Thread.sleep(4)
        }
    }

    private fun sendNoteOn(pitch: Int, velocity: Int, channel: Int) {
        // UMP MIDI: 0x90 | channel, pitch, velocity
        umpBuffer[0] = (0x90 or (channel and 0x0F)).toByte()
        umpBuffer[1] = pitch.coerceIn(0, 127).toByte()
        umpBuffer[2] = velocity.coerceIn(1, 127).toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    private fun sendNoteOff(pitch: Int, channel: Int) {
        umpBuffer[0] = (0x80 or (channel and 0x0F)).toByte()
        umpBuffer[1] = pitch.coerceIn(0, 127).toByte()
        umpBuffer[2] = 0.toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    private fun sendSustain(on: Boolean) {
        sustainOn = on
        umpBuffer[0] = (0xB0 or 0x00).toByte() // CC0 on channel 0
        umpBuffer[1] = 64.toByte() // Sustain CC
        umpBuffer[2] = if (on) 127.toByte() else 0.toByte()
        umpBuffer[3] = 0
        val bb = ByteBuffer.wrap(umpBuffer).order(ByteOrder.LITTLE_ENDIAN)
        plugin.addEventUmpInput(bb, 1)
    }

    override fun noteOn(pitch: Int, velocity: Int, channel: Int) {
        if (!running) return
        commands.add(Command.NoteOn(pitch.coerceIn(0, 127), velocity.coerceIn(1, 127), channel and 0x0F))
    }

    override fun noteOff(pitch: Int, channel: Int) {
        if (!running) return
        commands.add(Command.NoteOff(pitch.coerceIn(0, 127), channel and 0x0F))
    }

    override fun setSustain(on: Boolean) {
        if (!running) return
        commands.add(Command.Sustain(on))
    }

    override fun allNotesOff(fast: Boolean) {
        commands.add(Command.AllNotesOff(fast))
    }

    override fun setMasterVolume(volume: Float) {
        commands.add(Command.Volume(volume.coerceIn(0f, 4f)))
    }

    override fun close() {
        if (!running) return
        commands.add(Command.Shutdown)
        renderThread.interrupt()
        try {
            renderThread.join(2000)
        } catch (_: InterruptedException) {
        }
        plugin.deactivate()
        plugin.destroy()
        audioTrack.stop()
        audioTrack.release()
        running = false
    }

    companion object {
        const val CHANNEL_COUNT = 2
    }
}
