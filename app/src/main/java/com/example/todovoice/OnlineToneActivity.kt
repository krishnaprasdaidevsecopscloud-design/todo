package com.example.todovoice

import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Browse the online tone library: preview by streaming, "Use" downloads it. */
class OnlineToneActivity : AppCompatActivity() {

    private val tones = OnlineToneCatalog.tones
    private lateinit var adapter: ToneAdapter
    private lateinit var progress: ProgressBar
    private var previewPlayer: MediaPlayer? = null
    private var previewingUrl: String? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_online_tones)
        progress = findViewById(R.id.progressDownload)

        adapter = ToneAdapter()
        findViewById<RecyclerView>(R.id.recyclerTones).apply {
            layoutManager = LinearLayoutManager(this@OnlineToneActivity)
            adapter = this@OnlineToneActivity.adapter
        }
    }

    override fun onStop() {
        super.onStop()
        stopPreview()
    }

    private fun togglePreview(tone: OnlineTone) {
        val wasPlaying = previewingUrl == tone.url
        stopPreview()
        if (wasPlaying) return

        val mp = MediaPlayer()
        previewPlayer = mp
        previewingUrl = tone.url
        adapter.notifyDataSetChanged()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { stopPreview() }
            mp.setOnErrorListener { _, _, _ ->
                Toast.makeText(this, "Couldn't play preview. Check your internet connection.", Toast.LENGTH_SHORT).show()
                stopPreview()
                true
            }
            mp.setDataSource(tone.url)
            mp.prepareAsync()
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't play preview: ${e.message}", Toast.LENGTH_SHORT).show()
            stopPreview()
        }
    }

    private fun stopPreview() {
        previewPlayer?.release()
        previewPlayer = null
        if (previewingUrl != null) {
            previewingUrl = null
            adapter.notifyDataSetChanged()
        }
    }

    private fun useTone(tone: OnlineTone) {
        if (busy) return
        stopPreview()
        busy = true
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val uri = ToneDownloader.download(this@OnlineToneActivity, tone)
                setResult(RESULT_OK, Intent().putExtra(EXTRA_TONE_URI, uri.toString()))
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@OnlineToneActivity, "Download failed: ${e.message ?: "check your internet connection"}",
                    Toast.LENGTH_LONG
                ).show()
                busy = false
                progress.visibility = View.GONE
            }
        }
    }

    private inner class ToneAdapter : RecyclerView.Adapter<ToneAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.textToneName)
            val preview: Button = view.findViewById(R.id.btnTonePreview)
            val use: Button = view.findViewById(R.id.btnToneUse)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_online_tone, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val tone = tones[position]
            holder.name.text = tone.name
            holder.preview.text = if (previewingUrl == tone.url) "Stop" else "Preview"
            holder.preview.setOnClickListener { togglePreview(tone) }
            holder.use.setOnClickListener { useTone(tone) }
        }

        override fun getItemCount() = tones.size
    }

    companion object {
        const val EXTRA_TONE_URI = "extra_tone_uri"
    }
}
