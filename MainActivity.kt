package com.kidtube.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class MainActivity : Activity() {
    private data class VideoItem(val title: String, val path: String, val thumb: String)

    private val videos = mutableListOf<VideoItem>()
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var playerFrame: FrameLayout
    private lateinit var videoView: VideoView
    private lateinit var libraryGrid: GridLayout
    private lateinit var libraryScroll: ScrollView
    private lateinit var nowPlaying: TextView
    private lateinit var emptyMessage: TextView
    private var isFullscreen = false
    private var currentVideo: VideoItem? = null
    private val prefs by lazy { getSharedPreferences("kidtube_library", MODE_PRIVATE) }
    private val videoFolder by lazy { File(filesDir, "videos").apply { mkdirs() } }
    private val thumbFolder by lazy { File(filesDir, "thumbnails").apply { mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        loadLibrary()
        buildScreen()
        renderLibrary()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun buildScreen() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        setContentView(root)
        header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8)); setBackgroundColor(Color.WHITE)
        }
        val menu = TextView(this).apply {
            text = "☰"; textSize = 27f; setTextColor(Color.BLACK); gravity = Gravity.CENTER
            setOnClickListener { showMenu(it) }
        }
        header.addView(menu, LinearLayout.LayoutParams(dp(44), dp(48)))
        val logo = TextView(this).apply {
            text = "▶"; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; background = rounded(Color.rgb(230, 33, 23), 9)
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(28)))
        val brand = TextView(this).apply {
            text = "KidTube"; textSize = 23f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 25, 25)); setPadding(dp(9), 0, 0, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(0, dp(48), 1f))
        val add = TextView(this).apply {
            text = "+ Add"; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rounded(Color.rgb(230, 33, 23), 20)
            setPadding(dp(15), dp(9), dp(15), dp(9)); setOnClickListener { chooseVideo() }
        }
        header.addView(add)
        root.addView(header)

        playerFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        videoView = VideoView(this)
        playerFrame.addView(videoView, FrameLayout.LayoutParams(-1, -1))
        val full = TextView(this).apply {
            text = "⛶"; textSize = 25f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(0x66000000, 8); setPadding(dp(9), dp(4), dp(9), dp(4))
            setOnClickListener { toggleFullscreen() }
        }
        playerFrame.addView(full, FrameLayout.LayoutParams(dp(48), dp(44), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
        root.addView(playerFrame, LinearLayout.LayoutParams(-1, dp(220)))

        nowPlaying = TextView(this).apply {
            text = "Choose a video to start watching"; textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(35, 35, 35)); setPadding(dp(16), dp(13), dp(16), dp(9))
        }
        root.addView(nowPlaying)
        val section = TextView(this).apply {
            text = "Your videos"; textSize = 20f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(25, 25, 25)); setPadding(dp(16), dp(8), dp(16), dp(10))
        }
        root.addView(section)
        libraryScroll = ScrollView(this).apply { isFillViewport = true }
        libraryGrid = GridLayout(this).apply { columnCount = 2; setPadding(dp(10), 0, dp(10), dp(16)) }
        libraryScroll.addView(libraryGrid)
        root.addView(libraryScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        emptyMessage = TextView(this).apply {
            text = "No videos yet\nTap + Add to import videos from your phone."; textSize = 15f
            setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(dp(24), dp(25), dp(24), dp(25))
        }
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add("Add video").setOnMenuItemClickListener { chooseVideo(); true }
        popup.menu.add("Parent PIN").setOnMenuItemClickListener { parentPinMenu(); true }
        popup.menu.add("About KidTube").setOnMenuItemClickListener {
            AlertDialog.Builder(this).setTitle("KidTube").setMessage("A simple offline video library for children.")
                .setPositiveButton("OK", null).show(); true
        }
        popup.show()
    }

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "video/*"
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        startActivityForResult(intent, REQUEST_PICK_VIDEO)
    }

    @Deprecated("Legacy activity result used for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_VIDEO || resultCode != RESULT_OK || data == null) return
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip -> for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri) }
        if (uris.isEmpty()) data.data?.let { uris.add(it) }
        var added = 0
        uris.forEach { uri -> if (copyVideo(uri)) added++ }
        if (added > 0) { saveLibrary(); renderLibrary(); Toast.makeText(this, "$added video(s) added", Toast.LENGTH_SHORT).show() }
        else Toast.makeText(this, "Could not import the selected video", Toast.LENGTH_LONG).show()
    }

    private fun copyVideo(uri: Uri): Boolean {
        return try {
            val displayName = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "Video_${System.currentTimeMillis()}.mp4"
            val safeName = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").let { if (it.isBlank()) "video.mp4" else it }
            val destination = File(videoFolder, "${System.currentTimeMillis()}_$safeName")
            contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(destination).use { output -> input.copyTo(output) } }
                ?: return false
            val thumbnail = File(thumbFolder, destination.nameWithoutExtension + ".jpg")
            createThumbnail(destination, thumbnail)
            videos.add(VideoItem(displayName, destination.absolutePath, thumbnail.absolutePath))
            true
        } catch (_: Exception) { false }
    }

    private fun createThumbnail(video: File, output: File) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(video.absolutePath)
            val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            retriever.release()
        } catch (_: Exception) { }
    }

    private fun renderLibrary() {
        libraryGrid.removeAllViews()
        if (videos.isEmpty()) {
            libraryGrid.addView(emptyMessage, GridLayout.LayoutParams().apply { width = resources.displayMetrics.widthPixels - dp(20) })
            return
        }
        val width = (resources.displayMetrics.widthPixels - dp(30)) / 2
        videos.forEach { item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(5), dp(5), dp(10))
                background = rounded(Color.WHITE, 10); elevation = dp(1).toFloat()
                setOnClickListener { playVideo(item) }
                setOnLongClickListener { confirmDelete(item); true }
            }
            val imageFrame = FrameLayout(this).apply { background = rounded(Color.rgb(35, 35, 35), 8) }
            val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val thumb = File(item.thumb)
            if (thumb.exists() && thumb.length() > 0) image.setImageBitmap(BitmapFactory.decodeFile(thumb.absolutePath))
            else image.setImageResource(android.R.drawable.ic_media_play)
            imageFrame.addView(image, FrameLayout.LayoutParams(-1, -1))
            val play = TextView(this).apply {
                text = "▶"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                background = rounded(0x66000000, 30)
            }
            imageFrame.addView(play, FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER))
            card.addView(imageFrame, LinearLayout.LayoutParams(-1, dp(112)))
            val title = TextView(this).apply {
                text = item.title; textSize = 14f; typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.rgb(35, 35, 35)); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(5), dp(8), dp(5), 0)
            }
            card.addView(title)
            val params = GridLayout.LayoutParams().apply {
                width = width; setMargins(dp(2), dp(2), dp(2), dp(9))
            }
            libraryGrid.addView(card, params)
        }
    }

    private fun playVideo(item: VideoItem) {
        val file = File(item.path)
        if (!file.exists()) { Toast.makeText(this, "This video file is missing", Toast.LENGTH_SHORT).show(); return }
        currentVideo = item
        nowPlaying.text = item.title
        videoView.setVideoPath(file.absolutePath)
        videoView.setMediaController(MediaController(this).apply { setAnchorView(videoView) })
        videoView.setOnPreparedListener { it.start() }
        videoView.setOnErrorListener { _, _, _ ->
            Toast.makeText(this, "Unable to play this video format", Toast.LENGTH_LONG).show(); true
        }
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            header.visibility = View.GONE; nowPlaying.visibility = View.GONE
            libraryScroll.visibility = View.GONE
            window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
            playerFrame.layoutParams = LinearLayout.LayoutParams(-1, -1)
        } else exitFullscreen()
    }

    private fun exitFullscreen() {
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        header.visibility = View.VISIBLE; nowPlaying.visibility = View.VISIBLE
        libraryScroll.visibility = View.VISIBLE
        playerFrame.layoutParams = LinearLayout.LayoutParams(-1, dp(220))
    }

    @Deprecated("Back handling for compatibility")
    override fun onBackPressed() {
        if (isFullscreen) { exitFullscreen(); return }
        super.onBackPressed()
    }

    private fun parentPinMenu() {
        val savedPin = prefs.getString("parent_pin", null)
        if (savedPin == null) showPinDialog("Create parent PIN", null, true)
        else showPinDialog("Enter parent PIN", savedPin, false)
    }

    private fun showPinDialog(title: String, expected: String?, creating: Boolean) {
        val field = EditText(this).apply { inputType = 2; hint = "4-digit PIN"; setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this).setTitle(title).setView(field)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (creating) "Save" else "Continue") { _, _ ->
                val entered = field.text.toString()
                if (creating) {
                    if (entered.length == 4 && entered.all { it.isDigit() }) {
                        prefs.edit().putString("parent_pin", entered).apply()
                        Toast.makeText(this, "Parent PIN saved", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "Please enter exactly 4 digits", Toast.LENGTH_LONG).show()
                } else if (entered == expected) {
                    AlertDialog.Builder(this).setTitle("Parent settings")
                        .setItems(arrayOf("Change PIN", "Remove PIN")) { _, which ->
                            if (which == 0) showPinDialog("Create new parent PIN", null, true)
                            else prefs.edit().remove("parent_pin").apply()
                        }.setNegativeButton("Close", null).show()
                } else Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun confirmDelete(item: VideoItem) {
        AlertDialog.Builder(this).setTitle("Remove video?").setMessage("Remove ${item.title} from KidTube?")
            .setNegativeButton("Cancel", null).setPositiveButton("Remove") { _, _ ->
                if (currentVideo?.path == item.path) { videoView.stopPlayback(); currentVideo = null; nowPlaying.text = "Choose a video to start watching" }
                File(item.path).delete(); File(item.thumb).delete(); videos.remove(item)
                saveLibrary(); renderLibrary()
            }.show()
    }

    private fun loadLibrary() {
        val array = try { JSONArray(prefs.getString("videos", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val item = VideoItem(obj.optString("title"), obj.optString("path"), obj.optString("thumb"))
            if (File(item.path).exists()) videos.add(item)
        }
    }

    private fun saveLibrary() {
        val array = JSONArray()
        videos.forEach { item -> array.put(JSONObject().put("title", item.title).put("path", item.path).put("thumb", item.thumb)) }
        prefs.edit().putString("videos", array.toString()).apply()
    }

    companion object { private const val REQUEST_PICK_VIDEO = 401 }
}
