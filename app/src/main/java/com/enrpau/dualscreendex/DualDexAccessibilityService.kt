package com.enrpau.dualscreendex

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.enrpau.dualscreendex.data.RomManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors

class DualDexAccessibilityService : AccessibilityService() {

    // english uses the original latin model; japanese games use ML Kit's japanese model (reads kana + latin)
    private val latinRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val japaneseRecognizer by lazy { TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()) }

    private fun isJapanese() =
        getSharedPreferences("DualDexPrefs", MODE_PRIVATE).getString("SCAN_LANGUAGE", "en") == "ja"
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var repository: PokemonRepository
    private var pokemonList: List<Pokemon> = emptyList()

    private var isScanning = false
    private var lastScanTime = 0L
    private val SCAN_COOLDOWN = 600L // ms

    private val loopHandler = Handler(Looper.getMainLooper())
    private val loopRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
            } else {
                triggerScreenScan()
            }
            loopHandler.postDelayed(this, 1500L)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RomManager.initialize(this)
        repository = PokemonRepository(this)

        executor.submit {
            repository.reloadDatabase()
            pokemonList = repository.getAllPokemon()
            android.util.Log.d("DualDex_Service", "Service loaded ${pokemonList.size} Pokemon")
        }

        loopHandler.post(loopRunnable)
        android.util.Log.d("DualDex_Service", "Polling Loop Started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            triggerScreenScan()
        }
    }

    private fun triggerScreenScan() {
        val now = System.currentTimeMillis()
        if (isScanning || (now - lastScanTime) < SCAN_COOLDOWN) return

        isScanning = true
        lastScanTime = now

        val prefs = getSharedPreferences("DualDexPrefs", MODE_PRIVATE)
        val scanSource = prefs.getString("SCAN_SOURCE", "top") ?: "top"
        val targetDisplayId = getTargetDisplayId(scanSource)

        takeScreenshot(targetDisplayId, executor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val bitmap = try {
                    val buffer = screenshot.hardwareBuffer
                    Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, true)
                        .also { buffer.close() }
                } catch (e: Exception) {
                    null
                }

                if (bitmap != null) {
                    processImage(bitmap)
                } else {
                    isScanning = false
                }
            }

            override fun onFailure(errorCode: Int) {
                isScanning = false
                android.util.Log.e("DualDex_Service", "Screenshot failed on Display $targetDisplayId: $errorCode")
            }
        })
    }

    private fun getTargetDisplayId(scanSource: String): Int {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        return if (scanSource == "bottom" && displays.size > 1) {
            displays[1].displayId
        } else {
            if (displays.isNotEmpty()) displays[0].displayId else Display.DEFAULT_DISPLAY
        }
    }

    private fun processImage(bitmap: Bitmap) {
        val prefs = getSharedPreferences("DualDexPrefs", MODE_PRIVATE)
        val scanAlign = prefs.getString("SCAN_ALIGN", "left") ?: "left"

        val width = bitmap.width
        val height = bitmap.height

        val startY = 0
        val cropHeight = height / 2  // top half

        val startX = if (scanAlign == "right") width / 2 else 0
        val cropWidth = width / 2

        if (startY + cropHeight > height || startX + cropWidth > width) {
            isScanning = false
            return
        }

        try {
            val croppedBitmap = Bitmap.createBitmap(bitmap, startX, startY, cropWidth, cropHeight)
            val image = InputImage.fromBitmap(croppedBitmap, 0)

            (if (isJapanese()) japaneseRecognizer else latinRecognizer).process(image)
                .addOnSuccessListener { visionText ->
                    processOcrResult(visionText.text)
                    isScanning = false
                }
                .addOnFailureListener {
                    isScanning = false
                }
        } catch (e: Exception) {
            isScanning = false
        }
    }

    private fun processOcrResult(rawText: String) {
        val words = if (isJapanese()) {
            // keep kana / kanji, drop numbers and symbols, and skip UI labels like HP, Lv, No.
            rawText.replace("\n", " ")
                .replace(Regex("[^A-Za-z\\u3040-\\u30FF\\u4E00-\\u9FFF\\- ]"), " ")
                .replace(Regex("\\bNo\\b", RegexOption.IGNORE_CASE), " ")
                .split(Regex("\\s+"))
                .filter { w ->
                    val jp = w.any { isJapaneseChar(it) }
                    (if (jp) w.length >= 2 else w.length > 3) &&
                        !w.equals("HP", true) && !w.equals("Lv", true)
                }
        } else {
            // english: unchanged
            val cleanText = rawText.replace("\n", " ").replace(Regex("[^A-Za-z -]"), "")
            cleanText.split(" ").filter { it.length > 3 }
        }

        val foundNames = ArrayList<String>()
        val foundIds = ArrayList<Int>()
        val foundT1s = ArrayList<String>()
        val foundT2s = ArrayList<String>()

        var matchCount = 0

        for (word in words) {
            if (matchCount >= 2) break
            val match = findBestMatch(word)
            if (match != null) {
                if (!foundNames.contains(match.name)) {
                    foundNames.add(match.name)
                    foundIds.add(match.id)
                    foundT1s.add(match.type1.name)
                    foundT2s.add(match.type2?.name ?: "UNKNOWN")
                    matchCount++
                }
            }
        }

        val intent = Intent("com.enrpau.dualscreendex.POKEMON_DETECTED")
        intent.setPackage(packageName) // ensures only this app receives the broadcast

        if (foundNames.isNotEmpty()) {
            intent.putExtra("FOUND", true)
            intent.putStringArrayListExtra("NAMES", foundNames)
            intent.putIntegerArrayListExtra("IDS", foundIds)
            intent.putStringArrayListExtra("TYPE1S", foundT1s)
            intent.putStringArrayListExtra("TYPE2S", foundT2s)
        } else {
            intent.putExtra("FOUND", false)
        }

        sendBroadcast(intent)
    }

    private fun isJapaneseChar(ch: Char) = ch in '\u3040'..'\u30FF' || ch in '\u4E00'..'\u9FFF'

    // small kana read as their full-size forms (small i -> i, small tsu -> tsu) so OCR slips still match.
    // written as escapes: ァ->ア ィ->イ ゥ->ウ ェ->エ ォ->オ ャ->ヤ ュ->ユ ョ->ヨ ッ->ツ
    private val smallKana = mapOf(
        'ァ' to 'ア', 'ィ' to 'イ', 'ゥ' to 'ウ', 'ェ' to 'エ', 'ォ' to 'オ',
        'ャ' to 'ヤ', 'ュ' to 'ユ', 'ョ' to 'ヨ', 'ッ' to 'ツ'
    )

    private fun normalizeKana(input: String): String = String(CharArray(input.length) { smallKana[input[it]] ?: input[it] })

    /** Japanese names: exact katakana first, then a close match (at most 1 character off). */
    private fun findJapaneseMatch(input: String): Pokemon? {
        val norm = normalizeKana(input)
        pokemonList.find { it.japaneseKana != null && normalizeKana(it.japaneseKana) == norm }?.let { return it }
        var best: Pokemon? = null
        var bestDist = Int.MAX_VALUE
        for (p in pokemonList) {
            val kana = p.japaneseKana ?: continue
            if (kotlin.math.abs(kana.length - input.length) > 2) continue
            val dist = levenshtein(norm, normalizeKana(kana))
            if (dist <= 1 && dist < bestDist) { bestDist = dist; best = p }
        }
        return best
    }

    private fun findBestMatch(input: String): Pokemon? {
        if (input.any { isJapaneseChar(it) }) return findJapaneseMatch(input)

        val exact = pokemonList.find { it.name.equals(input, true) }
        if (exact != null) return exact

        if (input.isEmpty()) return null
        // the gender sign is often read as a letter glued to the name (MEOWTH♂ -> MEOWTHS), so try without it too
        val tries = listOfNotNull(input.lowercase(), input.dropLast(1).lowercase().takeIf { input.length > 4 })

        var bestPokemon: Pokemon? = null
        var bestDist = Int.MAX_VALUE

        for (p in pokemonList) {
            if (p.name.isEmpty()) continue
            val name = p.name.lowercase()
            val threshold = if (p.name.length < 6) 1 else 2
            for (t in tries) {
                if (kotlin.math.abs(t.length - name.length) > threshold) continue
                val dist = levenshtein(fold(t), fold(name))
                if (dist <= threshold && dist < bestDist) {
                    bestDist = dist
                    bestPokemon = p
                }
            }
        }
        return bestPokemon
    }

    /**
     * Letters the Game Boy Advance fonts (FireRed/LeafGreen especially) get misread as each other,
     * folded to one letter before comparing: M/W/N/H look alike, as do O/D/Q, I/L, U/V.
     */
    private fun fold(s: String): String = buildString(s.length) {
        for (c in s) append(when (c) {
            'm', 'w', 'n', 'h' -> 'h'
            'o', 'd', 'q' -> 'o'
            'i', 'l' -> 'i'
            'u', 'v' -> 'u'
            else -> c
        })
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val lhsLen = lhs.length
        val rhsLen = rhs.length
        var costs = IntArray(lhsLen + 1) { it }
        var newCosts = IntArray(lhsLen + 1)
        for (i in 1..rhsLen) {
            newCosts[0] = i
            for (j in 1..lhsLen) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                val costReplace = costs[j - 1] + match
                val costInsert = costs[j] + 1
                val costDelete = newCosts[j - 1] + 1
                newCosts[j] = minOf(costInsert, costDelete, costReplace)
            }
            val swap = costs
            costs = newCosts
            newCosts = swap
        }
        return costs[lhsLen]
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        // 3. STOP THE LOOP (Crucial to prevent battery drain/crashes)
        loopHandler.removeCallbacks(loopRunnable)
    }
}