package com.medit.daw

import android.content.Context
import android.content.pm.ServiceInfo
import org.androidaudioplugin.PluginInformation
import org.androidaudioplugin.PluginServiceInformation
import org.androidaudioplugin.hosting.AudioPluginHostHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Entry describing an installed AAP (AndroidAudioPlugin) plugin.
 */
data class AapEntry(
    val pluginId: String,
    val displayName: String,
    val developer: String?,
    val category: String?,
    val version: String?,
    val packageName: String,
    val isInstrument: Boolean,
    val parameterCount: Int,
    val portCount: Int,
)

/**
 * Library of installed AAP (AndroidAudioPlugin) plugins. Queries the system
 * for AAP plugin services and caches their metadata so tracks can select
 * AAP plugins as their sound source.
 */
class AapInstrumentLibrary(private val context: Context) {

    private val _entries = MutableStateFlow<List<AapEntry>>(emptyList())
    val entries: StateFlow<List<AapEntry>> = _entries.asStateFlow()

    /** Refresh the list of installed AAP plugins. */
    suspend fun refresh() {
        val list = withContext(Dispatchers.IO) {
            scanPlugins()
        }
        _entries.value = list
    }

    private suspend fun scanPlugins(): List<AapEntry> {
        return try {
            val plugins = AudioPluginHostHelper.queryAudioPlugins(context)
            plugins.mapNotNull { plugin ->
                try {
                    val serviceInfo = AudioPluginHostHelper.queryAudioPluginService(
                        context, plugin.pluginId.orEmpty()
                    )
                    val isInstrument = plugin.category ==
                        PluginInformation.PRIMARY_CATEGORY_INSTRUMENT
                    AapEntry(
                        pluginId = plugin.pluginId.orEmpty(),
                        displayName = plugin.displayName.orEmpty(),
                        developer = plugin.developer,
                        category = plugin.category,
                        version = plugin.version,
                        packageName = plugin.packageName.orEmpty(),
                        isInstrument = isInstrument,
                        parameterCount = plugin.getDeclaredParameterCount(),
                        portCount = plugin.getDeclaredPortCount(),
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns the [AapEntry] for [pluginId], or null if not found. */
    fun find(pluginId: String): AapEntry? =
        _entries.value.firstOrNull { it.pluginId == pluginId }

    /** True when at least one AAP plugin is installed. */
    fun isAvailable(): Boolean = _entries.value.isNotEmpty()

    fun shutdown() {
        // No resources to release at the library level; instances are managed
        // by the ViewModel via loadAapPlugin.
    }
}
