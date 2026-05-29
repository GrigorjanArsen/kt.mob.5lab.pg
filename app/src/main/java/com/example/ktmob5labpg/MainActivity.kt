package com.example.ktmob5labpg

import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.DecimalFormat

class MainActivity : AppCompatActivity() {

    private lateinit var tvFormula: TextView
    private lateinit var tvResult: TextView

    private var justCalculated = false
    private lateinit var musicMediaPlayer: MediaPlayer
    private var currentInput = ""
    private var currentResult = ""
    private var lastResult = ""

    private var mediaPlayer: MediaPlayer? = null

    // Звуки для действий
    private var soundAdd: MediaPlayer? = null
    private var soundSub: MediaPlayer? = null
    private var soundMul: MediaPlayer? = null
    private var soundDiv: MediaPlayer? = null
    private var soundEq: MediaPlayer? = null
    private var soundClear: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvFormula = findViewById(R.id.tvFormula)
        tvResult = findViewById(R.id.tvResult)

        loadSavedData()

        initSoundPlayers()
        initButtons()
        initMusicButton()
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
        } catch (e: Exception) {
            // Если звуков нет — просто не проигрываются
        }
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
                // Если только что посчитали и теперь жмём оператор — подставляем результат
                if (justCalculated) {
                    currentInput = tvResult.text.toString()
                    tvFormula.text = currentInput
                    justCalculated = false
                }
                // Проигрываем звук для оператора
                when (text) {
                    "+" -> playSound(soundAdd)
                    "-" -> playSound(soundSub)
                    "*" -> playSound(soundMul)
                    "/" -> playSound(soundDiv)
                }
                appendToFormula(text)
            }
            else -> {
                // Цифры и точка — без звука
                appendToFormula(text)
            }
        }
    }
    private fun clearAll() {
        currentInput = ""
        currentResult = ""
        tvFormula.text = ""
        tvResult.text = "0"
        justCalculated = false
        saveData()
    }

    private fun appendToFormula(value: String) {
        val operators = listOf('+', '-', '*', '/')  // Теперь это List<Char>

        if (justCalculated) {
            currentInput = ""
            tvFormula.text = ""
            justCalculated = false
        }

        // Получаем текущее число (после последнего оператора)
        val lastOperatorIndex = currentInput.indexOfLast { it in operators }
        val currentNumber = if (lastOperatorIndex == -1) {
            currentInput
        } else {
            currentInput.substring(lastOperatorIndex + 1)
        }

        // Если вводим цифру или точку
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

        // Если вводим оператор
        if (value.length == 1 && value[0] in operators) {
            if (currentInput.isEmpty() && value != "-") {
                return
            }

            // ЗАМЕНА последнего оператора на новый
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
        playSound(soundEq)
        if (currentInput.isEmpty()) return
        try {
            val result = evaluateExpression(currentInput)
            val formatted = DecimalFormat("0.##########").format(result)
            currentResult = formatted
            tvResult.text = currentResult
            // НЕ очищаем currentInput и tvFormula — оставляем выражение на месте
            justCalculated = true  // Флаг, что только что посчитали
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

        // Обработка первого отрицательного числа
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

    private fun initMusicButton() {
        val btnMusic = findViewById<ImageButton>(R.id.btnMusic)
        btnMusic.setOnClickListener {
            showMusicDialog()
        }
    }

    private fun showMusicDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_music, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnPlay = dialogView.findViewById<ImageButton>(R.id.btnPlay)
        val btnStop = dialogView.findViewById<ImageButton>(R.id.btnStop)
        val btnRestart = dialogView.findViewById<Button>(R.id.btnRestart)

        if (!::musicMediaPlayer.isInitialized) {
            musicMediaPlayer = MediaPlayer.create(this, R.raw.music)
            musicMediaPlayer.isLooping = true
        }

        btnPlay.setOnClickListener {
            if (!musicMediaPlayer.isPlaying) {
                musicMediaPlayer.start()
                Toast.makeText(this, "Музыка играет", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Музыка уже играет", Toast.LENGTH_SHORT).show()
            }
        }

        btnStop.setOnClickListener {
            if (musicMediaPlayer.isPlaying) {
                musicMediaPlayer.pause()
                Toast.makeText(this, "Музыка остановлена", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Музыка уже остановлена", Toast.LENGTH_SHORT).show()
            }
        }

        btnRestart.setOnClickListener {
            if (::musicMediaPlayer.isInitialized) {
                musicMediaPlayer.seekTo(0)  // перематываем на начало
                if (!musicMediaPlayer.isPlaying) {
                    musicMediaPlayer.start()  // если была на паузе — запускаем
                }
                Toast.makeText(this, "Трек начался заново", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        soundAdd?.release()
        soundSub?.release()
        soundMul?.release()
        soundDiv?.release()
        soundEq?.release()
        soundClear?.release()
        super.onDestroy()
    }
}