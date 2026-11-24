package com.example.instatrack

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.text.SimpleDateFormat
import java.util.*

class ProfileCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val prefs = context.getSharedPreferences("instatrack", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val profiles = try {
            val a = JSONArray(prefs.getString("profiles", "[]"))
            (0 until a.length()).map { a.getString(it) }
        } catch (_: Exception) { emptyList() }

        var changedProfiles = mutableListOf<String>()
        profiles.forEach { username ->
            try {
                val stats = fetchPublicStats(username) ?: return@forEach
                val old = lastStats(username)
                saveRecord(username, stats)
                if (old != null && (old.optString("followers") != stats.optString("followers") ||
                    old.optString("following") != stats.optString("following") ||
                    old.optString("posts") != stats.optString("posts"))) {
                    changedProfiles.add(username)
                }
            } catch (_: Exception) {
                // Keep previous data; never treat a blocked/failed request as zero.
            }
        }
        if (changedProfiles.isNotEmpty()) notifyChanges(changedProfiles)
        return Result.success()
    }

    private fun fetchPublicStats(username: String): JSONObject? {
        val url = "https://www.instagram.com/$username/"
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            .referrer("https://www.google.com/")
            .timeout(15000)
            .followRedirects(true)
            .get()

        val candidates = mutableListOf<String>()
        doc.select("meta[property=og:description], meta[name=description]").forEach {
            it.attr("content").takeIf(String::isNotBlank)?.let(candidates::add)
        }
        val text = candidates.joinToString(" ")
        // Accept only counts explicitly present in publicly served page metadata.
        val followers = Regex("""([\d,.]+[KkMm]?)\\s+Followers""").find(text)?.groupValues?.get(1)
        val following = Regex("""([\d,.]+[KkMm]?)\\s+Following""").find(text)?.groupValues?.get(1)
        val posts = Regex("""([\d,.]+[KkMm]?)\\s+Posts""").find(text)?.groupValues?.get(1)
        if (followers == null && following == null && posts == null) return null
        return JSONObject().put("followers", followers ?: "—")
            .put("following", following ?: "—").put("posts", posts ?: "—")
    }

    private fun lastStats(username: String): JSONObject? = try {
        val a = JSONArray(prefs.getString("history_$username", "[]"))
        if (a.length() == 0) null else a.getJSONObject(a.length()-1)
    } catch (_: Exception) { null }

    private fun saveRecord(username: String, stats: JSONObject) {
        val key = "history_$username"
        val a = try { JSONArray(prefs.getString(key, "[]")) } catch (_: Exception) { JSONArray() }
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        stats.put("time", stamp)
        a.put(stats)
        // Keep latest 500 samples per profile.
        val trimmed = JSONArray()
        for (i in maxOf(0, a.length()-500) until a.length()) trimmed.put(a.getJSONObject(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    private fun notifyChanges(names: List<String>) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel("changes", "Profile changes", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(applicationContext, "changes")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Instagram profile changes")
            .setContentText(names.joinToString(", ") { "@$it" } + " updated")
            .setStyle(NotificationCompat.BigTextStyle().bigText(names.joinToString("\n") { "@$it" }))
            .setAutoCancel(true).build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
