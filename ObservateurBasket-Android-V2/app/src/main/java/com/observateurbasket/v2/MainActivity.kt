package com.observateurbasket.v2

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.regex.Pattern

data class Actor(
    val id: String,
    var x: Float = 0.5f,
    var y: Float = 0.5f
)

data class Situation(
    val id: String,
    val quarter: Int,
    val type: String,
    val text: String,
    val actors: MutableList<Actor>,
    val time: String
)

data class QuarterNote(val quarter: Int, val text: String)

data class MatchData(
    val id: String,
    var name: String,
    var teamA: String,
    var teamB: String,
    var level: String,
    var ref1: String,
    var ref2: String,
    val date: String,
    var quarter: Int = 1,
    var finished: Boolean = false,
    val situations: MutableList<Situation> = mutableListOf(),
    val quarterNotes: MutableList<QuarterNote> = mutableListOf()
)

class MainActivity : Activity() {

    private val blue = Color.rgb(21, 101, 192)
    private val dark = Color.rgb(18, 59, 99)
    private val green = Color.rgb(46, 125, 50)
    private val red = Color.rgb(198, 40, 40)

    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var title: TextView

    private var match: MatchData? = null
    private var currentSituationType: String = ""
    private var currentTranscript = ""
    private var currentActors = mutableListOf<Actor>()
    private var listening = false
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var pendingVoiceCallback: ((String) -> Unit)? = null

    private val prefs by lazy { getSharedPreferences("observateur_v2", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun baseScreen(screenTitle: String): LinearLayout {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(245, 247, 250))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(dark)
        }

        title = TextView(this).apply {
            text = screenTitle
            textSize = 21f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        header.addView(title, LinearLayout.LayoutParams(0, -2, 1f))

        content = FrameLayout(this)
        root.addView(header)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        setContentView(root)
        return root
    }

    private fun text(value: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.rgb(35, 35, 35))
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(12, 8, 12, 8)
        }

    private fun button(label: String, color: Int = blue, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(color)
            setOnClickListener { action() }
            isAllCaps = false
        }

    private fun add(view: View, weight: Float = 0f) {
        val lp = LinearLayout.LayoutParams(-1, if (weight == 0f) -2 else 0, weight)
        lp.setMargins(10, 6, 10, 6)
        content.addView(view, lp)
    }

    private fun field(hint: String, value: String = ""): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            textSize = 16f
            setPadding(14, 4, 14, 4)
            setSingleLine(true)
        }

    private fun showHome() {
        baseScreen("🏀 Observateur Basket V2")
        add(text("Bienvenue dans votre carnet d'observation.", 18f, true))
        add(button("NOUVEAU MATCH", green) { showAdmin() })
        add(button("OUVRIR UN ANCIEN MATCH") { showHistory() })
        add(text("V2 — observation vocale + terrain + historique", 14f))
    }

    private fun showAdmin(existing: MatchData? = null) {
        baseScreen(if (existing == null) "Fiche administrative" else "Match")
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 4, 8, 12)
        }

        val name = field("Nom / identifiant du match", existing?.name ?: "")
        val teamA = field("Équipe A", existing?.teamA ?: "")
        val teamB = field("Équipe B", existing?.teamB ?: "")
        val level = field("Niveau / division", existing?.level ?: "")
        val ref1 = field("Arbitre R1 — prénom + nom", existing?.ref1 ?: "")
        val ref2 = field("Arbitre R2 — prénom + nom", existing?.ref2 ?: "")

        listOf(name, teamA, teamB, level, ref1, ref2).forEach { box.addView(it) }

        val save = button("ENREGISTRER LE MATCH", green) {
            val now = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(Date())
            val m = existing ?: MatchData(
                UUID.randomUUID().toString(),
                name.text.toString().ifBlank { "Match sans nom" },
                teamA.text.toString(),
                teamB.text.toString(),
                level.text.toString(),
                ref1.text.toString(),
                ref2.text.toString(),
                now
            )
            m.name = name.text.toString().ifBlank { "Match sans nom" }
            m.teamA = teamA.text.toString()
            m.teamB = teamB.text.toString()
            m.level = level.text.toString()
            m.ref1 = ref1.text.toString()
            m.ref2 = ref2.text.toString()
            match = m
            saveMatch()
            showMatchHome()
        }

        box.addView(save)
        content.addView(ScrollView(this).apply { addView(box) })
    }

    private fun showMatchHome() {
        val m = match ?: return
        baseScreen("🏀 ${m.name}")
        add(text("${m.teamA}  —  ${m.teamB}", 19f, true))
        add(text("${m.level}   •   ${m.date}", 14f))
        add(text("R1 : ${m.ref1}\nR2 : ${m.ref2}", 15f))

        if (m.finished) {
            add(button("VOIR LE RAPPORT", green) { showReport() })
            add(button("VOIR LES SITUATIONS") { showSituations() })
        } else {
            add(button("▶ DÉBUT DE MATCH", green) { showLive() })
            add(button("FIN DE QUART-TEMPS", red) { startQuarterVoice() })
        }
        add(button("HISTORIQUE") { showHistory() })
    }

    private fun showLive() {
        val m = match ?: return
        baseScreen("Quart ${m.quarter}")
        add(text("Match en cours : ${m.teamA} — ${m.teamB}", 18f, true))
        add(text("Choisissez le type de situation puis dictez votre observation.", 15f))

        val grid = GridLayout(this).apply {
            columnCount = 2
            rowCount = 3
        }
        listOf(
            "FAUTE" to red,
            "VIOLATION" to blue,
            "MÉCA" to Color.rgb(123, 31, 162),
            "GESTION" to Color.rgb(239, 108, 0),
            "AUTRE" to Color.rgb(0, 121, 107)
        ).forEach { (label, color) ->
            val b = button(label, color) { startSituationVoice(label) }
            grid.addView(b, GridLayout.LayoutParams().apply {
                width = 0
                height = 90
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(6, 6, 6, 6)
            })
        }
        add(grid)
        add(button("FIN DE QUART-TEMPS", red) { startQuarterVoice() })
        add(button("VOIR LES SITUATIONS") { showSituations() })
    }

    private fun startSituationVoice(type: String) {
        currentSituationType = type
        currentTranscript = ""
        startVoice { transcript ->
            currentTranscript = transcript
            currentActors = detectActors(transcript)
            showCourt()
        }
    }

    private fun startQuarterVoice() {
        val q = match?.quarter ?: 1
        currentSituationType = "BILAN Q$q"
        startVoice { transcript ->
            match?.quarterNotes?.add(QuarterNote(q, transcript))
            if (q >= 4) {
                match?.finished = true
                saveMatch()
                showReport()
            } else {
                match?.quarter = q + 1
                saveMatch()
                showLive()
            }
        }
    }

    private fun startVoice(done: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Reconnaissance vocale indisponible sur cet appareil.", Toast.LENGTH_LONG).show()
            done("")
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            pendingVoiceCallback = done
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 77)
            return
        }

        pendingVoiceCallback = done
        listening = true
        currentTranscript = ""

        baseScreen("🎙 Dictée en cours")
        add(text("Parlez normalement. La dictée continue après une pause.", 18f, true))
        val status = text("🎙 ÉCOUTE...", 18f, true)
        add(status)
        add(text("Quand vous avez terminé, appuyez sur :", 16f))
        add(button("J'AI FINI", red) {
            listening = false
            recognizer?.cancel()
            handler.removeCallbacksAndMessages(null)
            val result = currentTranscript.trim()
            pendingVoiceCallback = null
            done(result)
        })
        add(button("ANNULER") {
            listening = false
            recognizer?.cancel()
            handler.removeCallbacksAndMessages(null)
            pendingVoiceCallback = null
            showMatchHome()
        })

        launchRecognizer()
    }

    private fun launchRecognizer() {
        if (!listening) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer?.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                if (listening) handler.postDelayed({ launchRecognizer() }, 250)
            }
            override fun onError(error: Int) {
                if (listening) handler.postDelayed({ launchRecognizer() }, 350)
            }
            override fun onResults(results: Bundle?) {
                val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!list.isNullOrEmpty()) {
                    val sentence = list[0].trim()
                    if (sentence.isNotBlank()) {
                        currentTranscript = (currentTranscript + " " + sentence).trim()
                    }
                }
                if (listening) handler.postDelayed({ launchRecognizer() }, 200)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                // Les résultats partiels ne sont pas ajoutés pour éviter les doublons.
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRANCE.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 77 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingVoiceCallback?.let { startVoice(it) }
        } else {
            Toast.makeText(this, "Le micro est nécessaire pour la dictée.", Toast.LENGTH_LONG).show()
        }
    }

    private fun detectActors(text: String): MutableList<Actor> {
        val found = linkedSetOf<String>()
        val upper = text.uppercase(Locale.FRANCE)

        val pattern = Pattern.compile("\\b([AB])\\s*([0-9]{1,2})\\b")
        val matcher = pattern.matcher(upper)
        while (matcher.find()) found.add(matcher.group(1) + matcher.group(2))

        listOf("R1", "R2").forEach { if (upper.contains(it)) found.add(it) }

        val numberWords = mapOf(
            "UN" to 1, "UNE" to 1, "DEUX" to 2, "TROIS" to 3, "QUATRE" to 4,
            "CINQ" to 5, "SIX" to 6, "SEPT" to 7, "HUIT" to 8, "NEUF" to 9,
            "DIX" to 10, "ONZE" to 11, "DOUZE" to 12, "TREIZE" to 13,
            "QUATORZE" to 14, "QUINZE" to 15, "SEIZE" to 16, "DIX-SEPT" to 17,
            "DIX-HUIT" to 18, "DIX-NEUF" to 19, "VINGT" to 20
        )
        for ((word, number) in numberWords) {
            if (upper.contains("A $word") || upper.contains("A$word")) found.add("A$number")
            if (upper.contains("B $word") || upper.contains("B$word")) found.add("B$number")
        }

        found.add("R1")
        found.add("R2")

        return found.mapIndexed { index, id ->
            Actor(id, 0.15f + (index % 5) * 0.17f, 0.25f + (index / 5) * 0.20f)
        }.toMutableList()
    }

    private fun showCourt() {
        val m = match ?: return
        baseScreen("📐 Positionnement — $currentSituationType")
        add(text(currentTranscript.ifBlank { "Aucune dictée détectée." }, 15f))
        add(text("Déplacez les acteurs sur le terrain.", 15f, true))

        val court = CourtView(this, currentActors)
        add(court, 1f)

        add(button("FIN DE SITUATION", green) {
            val situation = Situation(
                UUID.randomUUID().toString(),
                m.quarter,
                currentSituationType,
                currentTranscript,
                currentActors.map { Actor(it.id, it.x, it.y) }.toMutableList(),
                SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date())
            )
            m.situations.add(situation)
            saveMatch()
            showLive()
        })
        add(button("RECOMMENCER LA DICTÉE") { startSituationVoice(currentSituationType) })
    }

    private fun showSituations() {
        val m = match ?: return
        baseScreen("📝 Situations enregistrées")
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        if (m.situations.isEmpty()) {
            list.addView(text("Aucune situation enregistrée.", 17f))
        } else {
            m.situations.forEachIndexed { index, s ->
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(8, 8, 8, 8)
                    setBackgroundColor(Color.WHITE)
                }
                box.addView(text("${index + 1}. Q${s.quarter} — ${s.type} — ${s.time}", 16f, true))
                box.addView(text(s.text.ifBlank { "(sans dictée)" }, 15f))
                box.addView(text("Acteurs : ${s.actors.joinToString(", ") { it.id }}", 14f))
                list.addView(box)
                list.addView(Space(this).apply {
                    layoutParams = LinearLayout.LayoutParams(-1, 8)
                })
            }
        }
        scroll.addView(list)
        add(scroll, 1f)
        add(button("RETOUR") { showMatchHome() })
    }

    private fun showReport() {
        val m = match ?: return
        baseScreen("📋 Rapport d'observation")
        val scroll = ScrollView(this)
        val report = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        report.addView(text("RAPPORT D'OBSERVATION", 22f, true))
        report.addView(text("${m.name}\n${m.teamA} — ${m.teamB}\n${m.level}\n${m.date}", 16f))

        report.addView(text("COMMENTAIRE GÉNÉRAL", 19f, true))
        report.addView(text(buildGeneralSummary(m), 15f))

        report.addView(text("SITUATIONS OBSERVÉES", 19f, true))
        m.situations.forEachIndexed { i, s ->
            report.addView(text("${i + 1}. Q${s.quarter} — ${s.type}", 16f, true))
            report.addView(text(s.text.ifBlank { "Aucune description." }, 15f))
        }

        report.addView(text("BILANS DE QUART-TEMPS", 19f, true))
        m.quarterNotes.forEach { q ->
            report.addView(text("Q${q.quarter}", 16f, true))
            report.addView(text(q.text, 15f))
        }

        report.addView(text("ARBITRE R1 — ${m.ref1}", 19f, true))
        report.addView(text(buildRefSummary(m, "R1"), 15f))
        report.addView(text("Points positifs :\n1. Qualité à confirmer à partir des situations observées.\n2. Capacité à exploiter les informations de match.\n3. Éléments positifs issus des bilans.", 15f))
        report.addView(text("Axes de travail :\n1. Renforcer la précision du positionnement lorsque les situations le montrent.\n2. Poursuivre le travail sur la communication et la gestion.\n3. Consolider les automatismes mécaniques.", 15f))

        report.addView(text("ARBITRE R2 — ${m.ref2}", 19f, true))
        report.addView(text(buildRefSummary(m, "R2"), 15f))
        report.addView(text("Points positifs :\n1. Bonne implication dans les situations relevées.\n2. Points favorables ressortant des observations.\n3. Progression à poursuivre sur les points maîtrisés.", 15f))
        report.addView(text("Axes de travail :\n1. Affiner le placement et la couverture des responsabilités.\n2. Travailler la communication et la gestion des situations.\n3. Consolider les mécanismes et la lecture du jeu.", 15f))

        scroll.addView(report)
        add(scroll, 1f)
        add(button("RETOUR AU MATCH") { showMatchHome() })
        add(button("ACCUEIL") { showHome() })
    }

    private fun buildGeneralSummary(m: MatchData): String {
        val count = m.situations.size
        val types = m.situations.groupingBy { it.type }.eachCount()
        val typeText = if (types.isEmpty()) "aucune catégorie" else types.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        return "Observation basée sur $count situation(s) enregistrée(s). " +
                "Répartition : $typeText. " +
                "Les bilans de quart-temps complètent l'analyse et doivent être croisés avec les situations détaillées avant le débrief final."
    }

    private fun buildRefSummary(m: MatchData, ref: String): String {
        val mentions = m.situations.count { s ->
            s.text.uppercase(Locale.FRANCE).contains(ref)
        }
        return "Le relevé contient $mentions situation(s) mentionnant explicitement $ref. " +
                "L'analyse finale doit croiser ces situations avec les bilans de quart-temps et le positionnement sur le terrain."
    }

    private fun showHistory() {
        baseScreen("📚 Anciens matchs")
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val ids = prefs.getStringSet("matches", emptySet()) ?: emptySet()
        if (ids.isEmpty()) {
            list.addView(text("Aucun match sauvegardé.", 17f))
        } else {
            ids.toList().sorted().forEach { id ->
                val json = prefs.getString("match_$id", null) ?: return@forEach
                val obj = JSONObject(json)
                val label = "${obj.optString("name")}\n${obj.optString("date")}\n${obj.optString("teamA")} — ${obj.optString("teamB")}"
                list.addView(button(label) {
                    match = fromJson(obj)
                    showMatchHome()
                })
            }
        }
        scroll.addView(list)
        add(scroll, 1f)
        add(button("NOUVEAU MATCH", green) { showAdmin() })
        add(button("ACCUEIL") { showHome() })
    }

    private fun saveMatch() {
        val m = match ?: return
        val ids = (prefs.getStringSet("matches", emptySet()) ?: emptySet()).toMutableSet()
        ids.add(m.id)
        prefs.edit()
            .putStringSet("matches", ids)
            .putString("match_${m.id}", toJson(m).toString())
            .apply()
    }

    private fun toJson(m: MatchData): JSONObject {
        val o = JSONObject()
        o.put("id", m.id)
        o.put("name", m.name)
        o.put("teamA", m.teamA)
        o.put("teamB", m.teamB)
        o.put("level", m.level)
        o.put("ref1", m.ref1)
        o.put("ref2", m.ref2)
        o.put("date", m.date)
        o.put("quarter", m.quarter)
        o.put("finished", m.finished)

        val situations = JSONArray()
        m.situations.forEach { s ->
            val so = JSONObject()
            so.put("id", s.id)
            so.put("quarter", s.quarter)
            so.put("type", s.type)
            so.put("text", s.text)
            so.put("time", s.time)
            val actors = JSONArray()
            s.actors.forEach { a ->
                actors.put(JSONObject().apply {
                    put("id", a.id); put("x", a.x); put("y", a.y)
                })
            }
            so.put("actors", actors)
            situations.put(so)
        }
        o.put("situations", situations)

        val notes = JSONArray()
        m.quarterNotes.forEach {
            notes.put(JSONObject().apply {
                put("quarter", it.quarter); put("text", it.text)
            })
        }
        o.put("quarterNotes", notes)
        return o
    }

    private fun fromJson(o: JSONObject): MatchData {
        val m = MatchData(
            o.optString("id"),
            o.optString("name"),
            o.optString("teamA"),
            o.optString("teamB"),
            o.optString("level"),
            o.optString("ref1"),
            o.optString("ref2"),
            o.optString("date"),
            o.optInt("quarter", 1),
            o.optBoolean("finished", false)
        )
        val arr = o.optJSONArray("situations") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.getJSONObject(i)
            val actors = mutableListOf<Actor>()
            val aa = s.optJSONArray("actors") ?: JSONArray()
            for (j in 0 until aa.length()) {
                val a = aa.getJSONObject(j)
                actors.add(Actor(a.optString("id"), a.optDouble("x", .5).toFloat(), a.optDouble("y", .5).toFloat()))
            }
            m.situations.add(
                Situation(s.optString("id"), s.optInt("quarter", 1), s.optString("type"),
                    s.optString("text"), actors, s.optString("time"))
            )
        }
        val notes = o.optJSONArray("quarterNotes") ?: JSONArray()
        for (i in 0 until notes.length()) {
            val n = notes.getJSONObject(i)
            m.quarterNotes.add(QuarterNote(n.optInt("quarter"), n.optString("text")))
        }
        return m
    }

    override fun onDestroy() {
        listening = false
        recognizer?.destroy()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
