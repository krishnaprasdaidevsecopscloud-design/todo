package com.example.todovoice

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lets the user pick an alarm tone from system tones, any audio file on the
 * phone / SD card (system file picker), or the online tone library.
 * Must be created before the activity is STARTED (e.g. as a property).
 * [onPicked] receives a tone Uri string, or null for "use default".
 */
class AlarmTonePicker(
    private val activity: ComponentActivity,
    private val onPicked: (String?) -> Unit
) {
    private val systemLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode != Activity.RESULT_OK) return@registerForActivityResult
            val uri = res.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            onPicked(uri?.toString())
        }

    private val fileLauncher =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            // keep read access after reboot, otherwise the alarm can't open the file later
            try {
                activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // provider doesn't offer persistable access; AlarmRingService falls back if it fails
            }
            onPicked(uri.toString())
        }

    private val onlineLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            res.data?.getStringExtra(OnlineToneActivity.EXTRA_TONE_URI)?.let(onPicked)
        }

    fun show(current: String?) {
        val options = arrayOf(
            "System alarm tones",
            "From phone storage / SD card",
            "Online tone library",
            "Use default tone"
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle("Choose alarm tone")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> systemLauncher.launch(systemPickerIntent(current))
                    1 -> fileLauncher.launch(arrayOf("audio/*"))
                    2 -> onlineLauncher.launch(Intent(activity, OnlineToneActivity::class.java))
                    3 -> onPicked(null)
                }
            }
            .show()
    }

    private fun systemPickerIntent(current: String?) =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Alarm tone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            // our downloaded tones are private file:// Uris; never hand those to another app
            current?.let(Uri::parse)?.takeIf { it.scheme != ContentResolver.SCHEME_FILE }?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, it)
            }
        }

    companion object {
        /** Human-readable name of a tone Uri; [nullLabel] when no tone is set. */
        fun displayName(context: Context, tone: String?, nullLabel: String): String {
            if (tone == null) return nullLabel
            val uri = Uri.parse(tone)
            if (uri.scheme == ContentResolver.SCHEME_FILE) {
                return uri.lastPathSegment.orEmpty().substringBeforeLast('.')
                    .replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { return it.substringBeforeLast('.') }
                }
            } catch (_: Exception) {
                // not every provider supports DISPLAY_NAME (e.g. settings default-tone Uris)
            }
            return try {
                RingtoneManager.getRingtone(context, uri)?.getTitle(context) ?: "Custom tone"
            } catch (_: Exception) {
                "Custom tone"
            }
        }
    }
}
