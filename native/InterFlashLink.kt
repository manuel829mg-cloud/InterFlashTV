package tv.own.owntv

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import org.koin.core.context.GlobalContext
import tv.own.owntv.core.repository.SourceRepository
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.ImportFinalizer
import tv.own.owntv.core.sync.SyncResult
import tv.own.owntv.core.model.SourceType

/** Each installation receives credentials only after its code is approved by the administrator. */
object InterFlashLink : Application.ActivityLifecycleCallbacks {
    private const val BASE = "https://interflash-isp-manager.up.railway.app"
    private var owner: Activity? = null
    private var job: Job? = null
    private var dialog: AlertDialog? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .followRedirects(false).build()

    fun install(app: Application) { app.registerActivityLifecycleCallbacks(this) }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MainActivity || job?.isActive == true) return
        owner = activity
        job = scope.launch {
            try { connect(activity) }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    dialog?.dismiss(); dialog = null
                    if (!activity.isFinishing) Toast.makeText(activity, R.string.if_link_retry, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun connect(activity: Activity) {
        val prefs = activity.getSharedPreferences("interflash-link", 0)
        val token = prefs.getString("token", null) ?: java.security.SecureRandom().let { rng ->
            val bytes = ByteArray(32); rng.nextBytes(bytes)
            bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        }.also { prefs.edit().putString("token", it).commit() }
        suspend fun config(): Pair<Int, JSONObject?> = withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url("$BASE/api/tv-native/config")
                .header("Authorization", "Bearer $token").build()).execute().use { r ->
                r.code to if (r.isSuccessful) JSONObject(r.body?.string() ?: "{}") else null
            }
        }
        var (status, data) = config()
        if (status == 403) {
            val body = JSONObject().put("token", token).put("name", android.os.Build.MODEL)
            val registration = client.newCall(Request.Builder().url("$BASE/api/tv-native/register")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()).execute().use { r ->
                check(r.isSuccessful); JSONObject(r.body?.string() ?: "{}")
            }
            val code = registration.getString("code")
            withContext(Dispatchers.Main) {
                if (!activity.isFinishing) {
                    dialog = AlertDialog.Builder(activity).setTitle(R.string.if_link_title)
                        .setMessage(activity.getString(R.string.if_link_code, code))
                        .setNegativeButton(R.string.if_link_later) { _, _ -> job?.cancel() }
                        .setOnCancelListener { job?.cancel() }.show()
                }
            }
            val deadline = System.currentTimeMillis() + 15 * 60 * 1000
            while (status == 403 && System.currentTimeMillis() < deadline) {
                delay(5000)
                val next = config(); status = next.first; data = next.second
            }
        }
        check(status == 200 && data != null)
        val payload = requireNotNull(data)
        val revision = payload.getInt("revision")
        val folderAdded = ensureDigital15Folder(activity)
        if (prefs.getInt("revision", -1) == revision) {
            if (folderAdded) withContext(Dispatchers.Main) {
                if (!activity.isFinishing) activity.recreate()
            }
            return
        }
        withContext(Dispatchers.Main) {
            dialog?.dismiss()
            if (!activity.isFinishing) dialog = AlertDialog.Builder(activity)
                .setTitle(R.string.if_link_title).setMessage(R.string.if_link_importing)
                .setCancelable(false).show()
        }
        val koin = GlobalContext.get()
        val repository = koin.get<SourceRepository>()
        val managed = prefs.getLong("source_id", -1).takeIf { it > 0 }?.let { repository.getById(it) }
        val sourceId: Long
        if (managed == null) {
            val importer = koin.get<SourceImporter>()
            val profile = koin.get<SettingsRepository>().activeProfileId.first()
            if (profile > 0) importer.useProfile(profile)
            else importer.createProfile(name=activity.getString(R.string.if_link_title), avatarId=0, isKids=false, pin=null)
            if (payload.getString("kind") == "xtream") importer.xtream(
                name=activity.getString(R.string.if_link_title), server=payload.getString("server"),
                username=payload.getString("username"), password=payload.getString("password"), makeDefault=true,
            ) else importer.m3u(name=activity.getString(R.string.if_link_title), url=payload.getString("url"), makeDefault=true)
            val result = importer.state.value as? SourceImporter.ImportState.Success ?: error("Import failed")
            sourceId = requireNotNull(result.source).id
            importer.finish()
        } else {
            val xtream = payload.getString("kind") == "xtream"
            val updated = managed.copy(
                type=if (xtream) SourceType.XTREAM else SourceType.M3U,
                url=payload.getString(if (xtream) "server" else "url"),
                username=if (xtream) payload.getString("username") else null,
                password=if (xtream) payload.getString("password") else null,
            )
            repository.updateSource(updated)
            val result = repository.sync(updated, onProgress={})
            check(result is SyncResult.Success)
            koin.get<ImportFinalizer>().finalize(updated)
            sourceId = updated.id
        }
        prefs.edit().putLong("source_id", sourceId).putInt("revision", revision).commit()
        ensureDigital15Folder(activity)
        withContext(Dispatchers.Main) {
            dialog?.dismiss(); dialog = null
            if (!activity.isFinishing) {
                Toast.makeText(activity, R.string.if_link_done, Toast.LENGTH_LONG).show()
                activity.recreate()
            }
        }
    }


    /** Adds a separate source/category without replacing the panel's provider or its VOD. */
    private suspend fun ensureDigital15Folder(activity: Activity): Boolean {
        val koin = GlobalContext.get()
        val repository = koin.get<SourceRepository>()
        val prefs = activity.getSharedPreferences("interflash-link", 0)
        val profile = koin.get<SettingsRepository>().activeProfileId.first()
        if (profile <= 0) return false
        val key = "digital15_source_" + profile
        val existing = prefs.getLong(key, -1)
        if (existing > 0 && repository.getById(existing) != null) return false
        val file = java.io.File(activity.filesDir, "interflash-digital15.m3u")
        activity.resources.openRawResource(R.raw.if_digital15).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val importer = koin.get<SourceImporter>()
        importer.useProfile(profile)
        importer.m3u(
            name = activity.getString(R.string.if_digital15_folder),
            url = file.absolutePath,
            makeDefault = false,
        )
        val result = importer.state.value as? SourceImporter.ImportState.Success ?: error("Import failed")
        val sourceId = requireNotNull(result.source).id
        importer.finish()
        prefs.edit().putLong(key, sourceId).commit()
        return true
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (owner === activity) { job?.cancel(); dialog?.dismiss(); dialog=null; owner=null }
    }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
}
