package tv.own.owntv.interflash

import android.app.Activity
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.database.entity.SourceEntity

/**
 * Opt-in TV-only pairing bridge. Uses the existing OwnTV source repository and parser;
 * no WebView, no duplicate player, no ISP customer/router/billing actions.
 */
object InterFlashTvAutoSync {
    fun start(activity: Activity, profiles: ProfileDao, sources: SourceRepository) {
        val prefs = activity.getSharedPreferences("interflash_tv_panel_sync", Activity.MODE_PRIVATE)
        val client = InterFlashTvPanelClient(activity)
        activity.lifecycleScope.launch {
            var pairingDialog: AlertDialog? = null
            var lastCode: String? = null
            while (!activity.isFinishing && !activity.isDestroyed) {
                try {
                    val pairing = client.register()
                    if (!pairing.approved) {
                        if (lastCode != pairing.code) {
                            pairingDialog?.dismiss()
                            lastCode = pairing.code
                            pairingDialog = AlertDialog.Builder(activity)
                                .setTitle(activity.getString(tv.own.owntv.R.string.if_tv_pair_title))
                                .setMessage(activity.getString(tv.own.owntv.R.string.if_tv_pair_instructions, pairing.code))
                                .setCancelable(false)
                                .setNegativeButton(activity.getString(android.R.string.cancel)) { _, _ -> }
                                .create()
                            pairingDialog.show()
                        }
                    } else {
                        pairingDialog?.dismiss()
                        pairingDialog = null
                        lastCode = null
                        when (val cfg = client.config()) {
                            is InterFlashTvPanelClient.TvConfig.Ready -> {
                                val all = profiles.getAllOnce()
                                val profileId = all.firstOrNull()?.id
                                if (profileId != null && prefs.getLong("revision", -1) != cfg.revision) {
                                    val existingId = prefs.getLong("sourceId", -1)
                                    val old = if (existingId > 0) sources.getById(existingId) else null
                                    val changed: SourceEntity = when (val src = cfg.source) {
                                        is InterFlashTvPanelClient.Source.M3u -> {
                                            if (old != null && old.type == tv.own.owntv.core.model.SourceType.M3U) {
                                                old.copy(url = src.url, name = cfg.name).also { sources.updateSource(it) }
                                            } else sources.addM3uSource(profileId, cfg.name, src.url)
                                        }
                                        is InterFlashTvPanelClient.Source.Xtream -> {
                                            if (old != null && old.type == tv.own.owntv.core.model.SourceType.XTREAM) {
                                                old.copy(url = src.server, username = src.username, password = src.password, name = cfg.name)
                                                    .also { sources.updateSource(it) }
                                            } else sources.addXtreamSource(profileId, cfg.name, src.server, src.username, src.password)
                                        }
                                    }
                                    val result = sources.sync(changed, onProgress = {})
                                    if (result is tv.own.owntv.core.sync.SyncResult.Success) {
                                        prefs.edit().putLong("revision", cfg.revision).putLong("sourceId", changed.id).apply()
                                    }
                                }
                            }
                            is InterFlashTvPanelClient.TvConfig.Suspended -> {
                                // Do not claim cached streams have been revoked: server-side URL
                                // and downstream playback authorization must be verified first.
                            }
                            else -> Unit
                        }
                    }
                } catch (cancel: CancellationException) {
                    pairingDialog?.dismiss()
                    throw cancel
                } catch (_: Exception) {
                    // Temporary network failures must not block normal OwnTV navigation.
                }
                delay(15_000)
            }
            pairingDialog?.dismiss()
        }
    }
}
