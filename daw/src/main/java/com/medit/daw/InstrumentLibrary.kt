package com.medit.daw

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.medit.midi.audio.SoundFont
import com.medit.midi.audio.SoundFontLoader
import com.medit.midi.audio.SoundFontPreset
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * Project-independent SoundFont library used by MeditDAW. The bundled piano is
 * always available; user banks are copied into app-private storage so a track
 * can keep referring to them after a restart.
 */
class InstrumentLibrary(private val context: android.content.Context) {

    data class Entry(
        val id: String,
        val name: String,
        val file: File?,
        val builtIn: Boolean,
    )

    private val dir: File = File(context.filesDir, "instruments").apply { mkdirs() }
    private val indexFile: File = File(dir, "library.json")
    private val cache = HashMap<String, SoundFont>()
    private val lock = Any()

    fun entries(): List<Entry> = synchronized(lock) {
        val list = mutableListOf(
            Entry(BUNDLED_ID, "Medit Piano", null, true),
        )
        for (item in readIndex()) {
            val f = File(dir, item.second)
            if (f.isFile && f.length() > 0) {
                list += Entry(item.first, item.third, f, false)
            }
        }
        list
    }

    fun displayName(id: String): String =
        entries().firstOrNull { it.id == id }?.name ?: "Medit Piano"

    /** Loads every playable preset of the requested bank, with caching. */
    fun font(id: String): SoundFont? = synchronized(lock) {
        cache[id]?.let { return it }
        val entry = entries().firstOrNull { it.id == id } ?: return null
        return try {
            val loaded = if (entry.builtIn) {
                context.assets.open("piano.sf2").use { input ->
                    SoundFontLoader.load(input.readBytes(), allPresets = true)
                }
            } else {
                val file = entry.file ?: return null
                SoundFontLoader.load(file, allPresets = true)
            }
            cache[id] = loaded
            loaded
        } catch (t: Throwable) {
            null
        }
    }

    fun presets(id: String): List<SoundFontPreset> =
        font(id)?.availablePresets ?: emptyList()

    fun import(resolver: ContentResolver, uri: Uri): Result<Entry> = runCatching {
        val (display, size) = queryMeta(resolver, uri)
        if (size != null && size > MAX_FILE_BYTES) {
            throw IOException("SoundFontが大きすぎます (最大1GB)")
        }
        val name = (display ?: "SoundFont.sf2").substringBeforeLast('.').ifBlank { "SoundFont" }
        val id = "sf" + System.currentTimeMillis().toString(36) +
            (0..999).random().toString().padStart(3, '0')
        val target = File(dir, "$id.sf2")
        val temp = File(dir, "$id.tmp")
        try {
            val stream = resolver.openInputStream(uri) ?: throw IOException("ファイルを開けません")
            stream.use { copyLimited(it, temp) }
            if (temp.length() == 0L) throw IOException("ファイルが空です")
            // Parse before publishing so a broken bank never enters the library.
            SoundFontLoader.load(temp, allPresets = true)
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
        } catch (t: Throwable) {
            temp.delete()
            target.delete()
            throw t
        }
        synchronized(lock) {
            val items = readIndex().toMutableList()
            items += Triple(id, target.name, name)
            writeIndex(items)
            cache.remove(id)
        }
        Entry(id, name, target, false)
    }

    fun delete(id: String) {
        synchronized(lock) {
            val items = readIndex().toMutableList()
            val removed = items.filter { it.first == id }
            items.removeAll { it.first == id }
            writeIndex(items)
            removed.forEach { File(dir, it.second).delete() }
            cache.remove(id)
        }
    }

    private fun readIndex(): List<Triple<String, String, String>> {
        if (!indexFile.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(indexFile.readText())
            val arr = root.optJSONArray("banks") ?: JSONArray()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "")
                    val file = o.optString("file", "")
                    val name = o.optString("name", "")
                    if (id.isNotBlank() && file.isNotBlank() && name.isNotBlank()) {
                        add(Triple(id, file, name))
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun writeIndex(items: List<Triple<String, String, String>>) {
        val arr = JSONArray()
        for ((id, file, name) in items) {
            arr.put(JSONObject().put("id", id).put("file", file).put("name", name))
        }
        val tmp = File(dir, "library.tmp")
        tmp.writeText(JSONObject().put("banks", arr).toString())
        val target = indexFile
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun copyLimited(input: InputStream, target: File) {
        target.outputStream().use { out ->
            val buffer = ByteArray(256 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IOException("SoundFontが大きすぎます")
                out.write(buffer, 0, n)
            }
        }
    }

    private fun queryMeta(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        if (name == null) name = uri.lastPathSegment?.substringAfterLast('/')
        return name to size
    }

    companion object {
        const val BUNDLED_ID = "bundled"
        private const val MAX_FILE_BYTES = 1L * 1024 * 1024 * 1024
    }
}
