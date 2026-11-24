package com.example.instatrack

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private val prefs by lazy { getSharedPreferences("instatrack", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scheduleChecks(this)
        askNotificationPermission()
        buildScreen()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 24)
            setBackgroundColor(0xFFF8F7FA.toInt())
        }
        val title = TextView(this).apply {
            text = "InstaTrack"
            textSize = 28f
            setTextColor(0xFF26202A.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Public profile monitor · automatic checks every 4 hours"
            textSize = 13f; setTextColor(0xFF706875.toInt())
            setPadding(0, 4, 0, 20)
        })
        val add = Button(this).apply {
            text = "+ Add Instagram username"
            setOnClickListener { addProfile() }
        }
        root.addView(add)
        val refresh = Button(this).apply {
            text = "Check now"
            setOnClickListener {
                OneTimeWorkRequestBuilder<ProfileCheckWorker>().build().also {
                    WorkManager.getInstance(this@MainActivity).enqueue(it)
                }
                Toast.makeText(this@MainActivity, "Check queued. Results may take a moment.", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(refresh)
        root.addView(TextView(this).apply {
            text = "TRACKED PROFILES"
            textSize = 12f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF706875.toInt()); setPadding(0, 22, 0, 8)
        })
        val names = getProfiles()
        if (names.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No profiles yet. Add a public Instagram username to begin."
                textSize = 15f; setTextColor(0xFF5E5662.toInt()); setPadding(0, 12, 0, 12)
            })
        }
        names.forEach { name ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(18, 14, 18, 14)
                setBackgroundColor(0xFFFFFFFF.toInt())
            }
            card.addView(TextView(this).apply {
                text = "@$name"; textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(0xFF29232D.toInt())
            })
            val latest = latestRecord(name)
            card.addView(TextView(this).apply {
                text = latest ?: "No successful check yet"
                textSize = 14f; setTextColor(0xFF625B67.toInt()); setPadding(0, 6, 0, 4)
            })
            card.addView(TextView(this).apply {
                text = "Latest history: " + historySummary(name)
                textSize = 12f; setTextColor(0xFF817987.toInt())
            })
            val remove = Button(this).apply {
                text = "Remove profile"
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity).setMessage("Remove @$name and its saved history?")
                        .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                            val list = getProfiles().toMutableSet(); list.remove(name)
                            prefs.edit().putString("profiles", JSONArray(list.toList()).toString()).remove("history_$name").apply()
                            buildScreen()
                        }.show()
                }
            }
            card.addView(remove)
            val lp = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 10 }
            root.addView(card, lp)
        }
        root.addView(TextView(this).apply {
            text = "Note: Instagram may block automated requests or omit counts. Failed checks do not erase saved history. Android may delay background work to save battery."
            textSize = 12f; setTextColor(0xFF817987.toInt()); setPadding(2, 22, 2, 8)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun addProfile() {
        val input = EditText(this).apply {
            hint = "instagram_username"
            singleLine = true
        }
        AlertDialog.Builder(this).setTitle("Add public profile").setView(input)
            .setNegativeButton("Cancel", null).setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim().removePrefix("@")
                if (!name.matches(Regex("[A-Za-z0-9._]{1,30}"))) {
                    Toast.makeText(this, "Enter a valid Instagram username.", Toast.LENGTH_LONG).show()
                } else {
                    val set = getProfiles().toMutableSet()
                    set.add(name)
                    prefs.edit().putString("profiles", JSONArray(set.toList()).toString()).apply()
                    buildScreen()
                }
            }.show()
    }

    private fun getProfiles(): List<String> = try {
        val a = JSONArray(prefs.getString("profiles", "[]"))
        (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }

    private fun latestRecord(name: String): String? = try {
        val a = JSONArray(prefs.getString("history_$name", "[]"))
        if (a.length() == 0) return null
        val o = a.getJSONObject(a.length()-1)
        "Followers: ${o.optString("followers","—")}  ·  Following: ${o.optString("following","—")}  ·  Posts: ${o.optString("posts","—")}\n${o.optString("time","")}"
    } catch (_: Exception) { null }

    private fun historySummary(name: String): String = try {
        val a = JSONArray(prefs.getString("history_$name", "[]"))
        "${a.length()} saved checks"
    } catch (_: Exception) { "0 saved checks" }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 44)
        }
    }
}

fun scheduleChecks(context: Context) {
    val request = PeriodicWorkRequestBuilder<ProfileCheckWorker>(4, TimeUnit.HOURS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("instatrack_periodic", ExistingPeriodicWorkPolicy.KEEP, request)
}
