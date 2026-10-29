package tv.own.owntv.interflash

import android.app.Activity
import androidx.activity.ComponentActivity
import android.app.AlertDialog
import android.util.Log
import android.widget.Toast
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
    fun start(activity: ComponentActivity, profiles: ProfileDao, sources: SourceRepository, player: tv.own.owntv.player.OwnTVPlayer) {
        val prefs = activity.getSharedPreferences("interflash_tv_panel_sync", Activity.MODE_PRIVATE)
        val client = InterFlashTvPanelClient(activity)
        activity.lifecycleScope.launch {
            var pairingDialog: AlertDialog? = null
            var lastCode: String? = null
            var lastDiagnostic: String? = null
            fun tellOnce(message: String) {
                if (lastDiagnostic != message) {
                    lastDiagnostic = message
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                }
            }
            while (!activity.isFinishing && !activity.isDestroyed) {
                try {
                    val pairing = client.register()
                    if (pairing.revoked) {
                        pairingDialog?.dismiss()
                        pairingDialog = null
                        lastCode = null
                        // Stop current playback before revoking the single panel-managed source.
                        player.onAppBackgrounded()
                        player.discardBackgroundRestore()
                        val sourceId = prefs.getLong("sourceId", -1)
                        if (sourceId > 0) {
                            sources.getById(sourceId)?.let { sources.deleteSource(it) }
                            prefs.edit().remove("sourceId").remove("revision").apply()
                        }
                        val manualId = prefs.getLong("manualSourceId", -1)
                        if (manualId > 0) {
                            sources.getById(manualId)?.let { sources.deleteSource(it) }
                            prefs.edit().remove("manualSourceId").remove("manualRevision").apply()
                        }
                        tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_revoked))
                    } else if (!pairing.approved) {
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
                                if (profileId == null) tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_missing_profile))
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
                                    prefs.edit().putLong("sourceId", changed.id).apply()
                                    tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_sync_start))
                                    val result = sources.sync(changed, onProgress = {})
                                    if (result is tv.own.owntv.core.sync.SyncResult.Success) {
                                        prefs.edit().putLong("revision", cfg.revision).putLong("sourceId", changed.id).apply()
                                        tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_sync_success))
                                    }
                                    else {
                                        Log.w("InterFlashTV", "Sync returned failure: " + result.javaClass.simpleName)
                                        tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_sync_failed))
                                    }
                                }
                                // Independent Dominican TV source; existing primary source stays intact.
                                val manualOldId = prefs.getLong("manualSourceId", -1)
                                val manualOld = if (manualOldId > 0) sources.getById(manualOldId) else null
                                if (cfg.manualUrl == null) {
                                    if (manualOld != null) sources.deleteSource(manualOld)
                                    prefs.edit().remove("manualSourceId").remove("manualRevision").apply()
                                } else if (profileId != null &&
                                    (prefs.getLong("manualRevision", -1) != cfg.manualRevision ||
                                     manualOld == null || manualOld.url != cfg.manualUrl)) {
                                    val manualSource = if (manualOld != null &&
                                        manualOld.type == tv.own.owntv.core.model.SourceType.M3U) {
                                        manualOld.copy(url = cfg.manualUrl, name = activity.getString(tv.own.owntv.R.string.if_tv_dominican_folder))
                                            .also { sources.updateSource(it) }
                                    } else sources.addM3uSource(profileId, activity.getString(tv.own.owntv.R.string.if_tv_dominican_folder), cfg.manualUrl)
                                    prefs.edit().putLong("manualSourceId", manualSource.id).apply()
                                    val manualResult = sources.sync(manualSource, onProgress = {})
                                    if (manualResult is tv.own.owntv.core.sync.SyncResult.Success) {
                                        prefs.edit().putLong("manualRevision", cfg.manualRevision).apply()
                                    } else {
                                        Log.w("InterFlashTV", "Dominican channel sync failed")
                                        tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_sync_failed))
                                    }
                                }
                            }
                            is InterFlashTvPanelClient.TvConfig.Suspended -> {
                                // Subscription expired: stop playback and remove ONLY the panel-owned
                                // source. Other user-added sources and unrelated data remain untouched.
                                player.onAppBackgrounded()
                                player.discardBackgroundRestore()
                                val sourceId = prefs.getLong("sourceId", -1)
                                if (sourceId > 0) {
                                    sources.getById(sourceId)?.let { sources.deleteSource(it) }
                                    prefs.edit().remove("sourceId").remove("revision").apply()
                                }
                                val manualId = prefs.getLong("manualSourceId", -1)
                        if (manualId > 0) {
                            sources.getById(manualId)?.let { sources.deleteSource(it) }
                            prefs.edit().remove("manualSourceId").remove("manualRevision").apply()
                        }
                                tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_payment_expired))
                            }
                            else -> Unit
                        }
                    }
                } catch (cancel: CancellationException) {
                    pairingDialog?.dismiss()
                    throw cancel
                } catch (e: Exception) {
                    Log.e("InterFlashTV", "TV sync: " + e.javaClass.simpleName, e)
                    tellOnce(activity.getString(tv.own.owntv.R.string.if_tv_sync_failed))
                }
                delay(15_000)
            }
            pairingDialog?.dismiss()
        }
    }
}
