package com.example.ktmob5labpg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var tvFormula: TextView
    private lateinit var tvResult: TextView

    private var currentInput = ""
    private var currentResult = ""
    private var lastResult = ""
    private var justCalculated = false

    // Музыкальный плеер
    private var musicMediaPlayer: MediaPlayer? = null
    private var currentTrackIndex = 0
    private var isRepeatEnabled = false
    private val trackFileNames = listOf("song_one", "song_two", "song_three")
    private val trackDisplayNames = mutableListOf("", "", "")
    private val trackArtists = mutableListOf("", "", "")
    private val trackCovers = mutableListOf<Bitmap?>(null, null, null)

    // Звуки для кнопок калькулятора
    private var soundAdd: MediaPlayer? = null
    private var soundSub: MediaPlayer? = null
    private var soundMul: MediaPlayer? = null
    private var soundDiv: MediaPlayer? = null
    private var soundEq: MediaPlayer? = null
    private var soundClear: MediaPlayer? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFormula = findViewById(R.id.tvFormula)
        tvResult = findViewById(R.id.tvResult)

        loadSavedData()
        initSoundPlayers()
        initButtons()
        initMusicButton()
        loadAllTracksMetadata()
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences("calc_data", MODE_PRIVATE)
        currentInput = prefs.getString("current_input", "") ?: ""
        lastResult = prefs.getString("last_result", "") ?: ""
        if (currentInput.isNotEmpty()) {
            tvFormula.text = currentInput
        }
        if (lastResult.isNotEmpty()) {
            tvResult.text = lastResult
        } else {
            tvResult.text = "0"
        }
    }

    private fun saveData() {
        val prefs = getSharedPreferences("calc_data", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("current_input", currentInput)
        editor.putString("last_result", currentResult.ifEmpty { tvResult.text.toString() })
        editor.apply()
    }

    private fun initSoundPlayers() {
        try {
            soundAdd = MediaPlayer.create(this, R.raw.sound_add)
            soundSub = MediaPlayer.create(this, R.raw.sound_sub)
            soundMul = MediaPlayer.create(this, R.raw.sound_mul)
            soundDiv = MediaPlayer.create(this, R.raw.sound_div)
            soundEq = MediaPlayer.create(this, R.raw.sound_eq)
            soundClear = MediaPlayer.create(this, R.raw.sound_clear)
        } catch (e: Exception) { }
    }

    private fun playSound(media: MediaPlayer?) {
        try {
            media?.let {
                if (it.isPlaying) it.pause()
                it.seekTo(0)
                it.start()
            }
        } catch (e: Exception) { }
    }

    private fun initButtons() {
        val buttonIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4, R.id.btn5,
            R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9, R.id.btnDot,
            R.id.btnPlus, R.id.btnMinus, R.id.btnMultiply, R.id.btnDivide,
            R.id.btnEquals, R.id.btnClear
        )
        for (id in buttonIds) {
            findViewById<Button>(id).setOnClickListener { onButtonClick(it) }
        }
    }

    private fun onButtonClick(view: android.view.View) {
        val btn = view as Button
        val text = btn.text.toString()

        when (text) {
            "C" -> {
                playSound(soundClear)
                clearAll()
            }
            "=" -> {
                playSound(soundEq)
                calculateResult()
            }
            "+", "-", "*", "/" -> {
                if (justCalculated) {
                    currentInput = tvResult.text.toString()
                    tvFormula.text = currentInput
                    justCalculated = false
                }
                when (text) {
                    "+" -> playSound(soundAdd)
                    "-" -> playSound(soundSub)
                    "*" -> playSound(soundMul)
                    "/" -> playSound(soundDiv)
                }
                appendToFormula(text)
            }
            else -> {
                appendToFormula(text)
            }
        }
    }

    private fun appendToFormula(value: String) {
        val operators = listOf('+', '-', '*', '/')

        if (justCalculated) {
            currentInput = ""
            tvFormula.text = ""
            justCalculated = false
        }

        val lastOperatorIndex = currentInput.indexOfLast { it in operators }
        val currentNumber = if (lastOperatorIndex == -1) {
            currentInput
        } else {
            currentInput.substring(lastOperatorIndex + 1)
        }

        if (value !in operators.map { it.toString() }) {
            val isNegative = currentNumber.startsWith("-") && currentNumber.length > 0
            val numLength = if (isNegative) currentNumber.length - 1 else currentNumber.length

            if (numLength >= 10 && value != ".") {
                Toast.makeText(this, "Число не может быть длиннее 10 цифр", Toast.LENGTH_SHORT).show()
                return
            }

            if (value == "." && currentNumber.contains(".")) {
                return
            }
        }

        if (value.length == 1 && value[0] in operators) {
            if (currentInput.isEmpty() && value != "-") {
                return
            }

            if (currentInput.isNotEmpty() && currentInput.last() in operators) {
                if (currentInput.last() == '-' && value == "-") {
                    return
                }
                currentInput = currentInput.dropLast(1) + value
                tvFormula.text = currentInput
                saveData()
                return
            }
        }

        currentInput += value
        tvFormula.text = currentInput
        saveData()
    }

    private fun calculateResult() {
        if (currentInput.isEmpty()) return
        try {
            val result = evaluateExpression(currentInput)
            val formatted = DecimalFormat("0.##########").format(result)
            currentResult = formatted
            tvResult.text = currentResult
            justCalculated = true
            saveData()
        } catch (e: Exception) {
            tvResult.text = "Ошибка"
            Toast.makeText(this, "Неверное выражение", Toast.LENGTH_SHORT).show()
        }
    }

    private fun evaluateExpression(expression: String): Double {
        var expr = expression
        var result = 0.0
        var currentNumber = 0.0
        var lastOperator = '+'
        var i = 0

        if (expr.startsWith('-')) {
            expr = "0$expr"
        }

        while (i < expr.length) {
            val ch = expr[i]
            if (ch.isDigit() || ch == '.') {
                var j = i
                while (j < expr.length && (expr[j].isDigit() || expr[j] == '.')) {
                    j++
                }
                currentNumber = expr.substring(i, j).toDouble()
                when (lastOperator) {
                    '+' -> result += currentNumber
                    '-' -> result -= currentNumber
                    '*' -> result *= currentNumber
                    '/' -> {
                        if (currentNumber == 0.0) throw Exception("Деление на ноль")
                        result /= currentNumber
                    }
                }
                i = j
            } else if (ch == '+' || ch == '-' || ch == '*' || ch == '/') {
                lastOperator = ch
                i++
            } else {
                i++
            }
        }
        return result
    }

    private fun clearAll() {
        currentInput = ""
        currentResult = ""
        tvFormula.text = ""
        tvResult.text = "0"
        justCalculated = false
        saveData()
    }

    private fun initMusicButton() {
        val btnMusic = findViewById<ImageButton>(R.id.btnMusic)
        btnMusic.setOnClickListener {
            showMusicDialog()
        }
    }

    private fun loadAllTracksMetadata() {
        for (i in trackFileNames.indices) {
            loadMetadataFromAsset(trackFileNames[i], i)
        }
    }

    private fun loadMetadataFromAsset(fileName: String, index: Int) {
        val retriever = MediaMetadataRetriever()
        try {
            val afd = assets.openFd("$fileName.mp3")
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            trackDisplayNames[index] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName
            trackArtists[index] = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Неизвестный исполнитель"

            val artworkBytes = retriever.embeddedPicture
            trackCovers[index] = if (artworkBytes != null) {
                BitmapFactory.decodeByteArray(artworkBytes, 0, artworkBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            trackDisplayNames[index] = fileName
            trackArtists[index] = "Неизвестный исполнитель"
            trackCovers[index] = null
        } finally {
            retriever.release()
        }
    }

    private fun showMusicDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_music, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val ivCover = dialogView.findViewById<ImageView>(R.id.ivCover)
        val tvTrackName = dialogView.findViewById<TextView>(R.id.tvTrackName)
        val tvArtist = dialogView.findViewById<TextView>(R.id.tvArtist)
        val btnPrevious = dialogView.findViewById<ImageButton>(R.id.btnPrevious)
        val btnPlay = dialogView.findViewById<ImageButton>(R.id.btnPlay)
        val btnPause = dialogView.findViewById<ImageButton>(R.id.btnPause)
        val btnNext = dialogView.findViewById<ImageButton>(R.id.btnNext)
        val btnRepeat = dialogView.findViewById<ImageButton>(R.id.btnRepeat)
        val seekBar = dialogView.findViewById<SeekBar>(R.id.seekBar)
        val tvCurrentTime = dialogView.findViewById<TextView>(R.id.tvCurrentTime)
        val tvDuration = dialogView.findViewById<TextView>(R.id.tvDuration)

        var isSeeking = false
        var isUserSwitching = false

        fun formatTime(seconds: Int): String {
            val mins = seconds / 60
            val secs = seconds % 60
            return String.format("%d:%02d", mins, secs)
        }

        fun updateUIForCurrentTrack() {
            tvTrackName.text = trackDisplayNames[currentTrackIndex]
            tvArtist.text = trackArtists[currentTrackIndex]
            val cover = trackCovers[currentTrackIndex]
            if (cover != null) {
                ivCover.setImageBitmap(cover)
            } else {
                ivCover.setImageResource(R.drawable.ic_default_cover)
            }
        }

        fun releasePlayer() {
            musicMediaPlayer?.let {
                try {
                    if (it.isPlaying) it.stop()
                    it.release()
                } catch (e: Exception) { }
            }
            musicMediaPlayer = null
        }

        fun initPlayer(index: Int, autoStart: Boolean = false) {
            releasePlayer()
            try {
                val afd = assets.openFd("${trackFileNames[index]}.mp3")
                musicMediaPlayer = MediaPlayer().apply {
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    prepare()
                }
                afd.close()

                musicMediaPlayer?.let { mp ->
                    val total = mp.duration / 1000
                    if (total > 0) {
                        tvDuration.text = formatTime(total)
                        seekBar.max = 100
                        seekBar.progress = 0
                        tvCurrentTime.text = formatTime(0)
                    }
                }
                updateUIForCurrentTrack()

                if (autoStart) {
                    musicMediaPlayer?.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Ошибка загрузки трека", Toast.LENGTH_SHORT).show()
            }
        }

        fun setupCompletionListener() {
            musicMediaPlayer?.setOnCompletionListener {
                if (!isUserSwitching) {
                    if (!isRepeatEnabled) {
                        currentTrackIndex = (currentTrackIndex + 1) % trackFileNames.size
                        mainHandler.post {
                            initPlayer(currentTrackIndex, true)
                            setupCompletionListener()
                        }
                    } else {
                        mainHandler.post {
                            musicMediaPlayer?.seekTo(0)
                            musicMediaPlayer?.start()
                        }
                    }
                }
            }
        }

        val updateSeekBar = object : Runnable {
            override fun run() {
                musicMediaPlayer?.let { mp ->
                    if (!isSeeking && mp.isPlaying) {
                        val current = mp.currentPosition / 1000
                        val total = mp.duration / 1000
                        if (total > 0) {
                            seekBar.progress = (current * 100 / total)
                            tvCurrentTime.text = formatTime(current)
                        }
                    }
                }
                dialogView.postDelayed(this, 500)
            }
        }

        // Инициализация
        if (musicMediaPlayer == null) {
            initPlayer(currentTrackIndex, false)
            setupCompletionListener()
        } else {
            // Обновляем UI для текущего трека
            updateUIForCurrentTrack()
            val total = musicMediaPlayer?.duration?.div(1000) ?: 0
            if (total > 0) {
                tvDuration.text = formatTime(total)
                seekBar.max = 100
                val current = musicMediaPlayer?.currentPosition?.div(1000) ?: 0
                seekBar.progress = (current * 100 / total)
                tvCurrentTime.text = formatTime(current)
            }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    musicMediaPlayer?.let { mp ->
                        val total = mp.duration / 1000
                        val newPosition = (progress / 100.0) * total
                        tvCurrentTime.text = formatTime(newPosition.toInt())
                    }
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isSeeking = false
                musicMediaPlayer?.let { mp ->
                    val total = mp.duration
                    val newPosition = (seekBar?.progress ?: 0) * total / 100
                    mp.seekTo(newPosition)
                }
            }
        })

        btnPlay.setOnClickListener {
            musicMediaPlayer?.let {
                if (!it.isPlaying) {
                    it.start()
                    Toast.makeText(this, "▶ ${trackDisplayNames[currentTrackIndex]}", Toast.LENGTH_SHORT).show()
                    updateSeekBar.run()
                }
            }
        }

        btnPause.setOnClickListener {
            musicMediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    Toast.makeText(this, "⏸ Пауза", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnPrevious.setOnClickListener {
            isUserSwitching = true
            currentTrackIndex = if (currentTrackIndex - 1 < 0) trackFileNames.size - 1 else currentTrackIndex - 1
            initPlayer(currentTrackIndex, true)
            setupCompletionListener()
            Toast.makeText(this, "◀ ${trackDisplayNames[currentTrackIndex]}", Toast.LENGTH_SHORT).show()
            updateSeekBar.run()
            isUserSwitching = false
        }

        btnNext.setOnClickListener {
            isUserSwitching = true
            currentTrackIndex = (currentTrackIndex + 1) % trackFileNames.size
            initPlayer(currentTrackIndex, true)
            setupCompletionListener()
            Toast.makeText(this, "${trackDisplayNames[currentTrackIndex]} ▶", Toast.LENGTH_SHORT).show()
            updateSeekBar.run()
            isUserSwitching = false
        }

        btnRepeat.setOnClickListener {
            isRepeatEnabled = !isRepeatEnabled
            val status = if (isRepeatEnabled) "ВКЛ" else "ВЫКЛ"
            Toast.makeText(this, "Повтор $status", Toast.LENGTH_SHORT).show()
        }

        dialog.setOnDismissListener {
            dialogView.removeCallbacks(updateSeekBar)
        }

        dialog.show()
    }

    override fun onDestroy() {
        soundAdd?.release()
        soundSub?.release()
        soundMul?.release()
        soundDiv?.release()
        soundEq?.release()
        soundClear?.release()
        musicMediaPlayer?.release()
        super.onDestroy()
    }
}