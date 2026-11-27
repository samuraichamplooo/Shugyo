package dev.shugyo.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps every study set on the phone: files/decks/<id>/deck.json, its pictures, and progress.json.
 */
object Store {
    private const val TAG = "Store"
    private lateinit var root: File
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _decks = MutableStateFlow<List<Deck>>(emptyList())
    val decks: StateFlow<List<Deck>> = _decks

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    /** Goes up whenever progress changes, so screens showing it redraw. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val progress = ConcurrentHashMap<String, Progress>()

    fun init(context: Context) {
        root = File(context.filesDir, "decks").apply { mkdirs() }
        io.launch {
            val list = ArrayList<Deck>()
            root.listFiles()?.forEach { dir ->
                val f = File(dir, "deck.json")
                if (!f.exists()) return@forEach
                try {
                    list.add(Deck.fromJson(JSONObject(f.readText())))
                    val pf = File(dir, "progress.json")
                    if (pf.exists()) progress[dir.name] = Progress.fromJson(JSONObject(pf.readText()))
                } catch (e: Exception) {
                    Log.w(TAG, "couldn't read ${dir.name}", e)
                }
            }
            _decks.value = list.sortedByDescending { it.created }
            _loaded.value = true
        }
    }

    fun newId(): String = "d" + System.currentTimeMillis().toString(36)

    fun dir(id: String) = File(root, id).apply { mkdirs() }

    fun deck(id: String): Deck? = _decks.value.firstOrNull { it.id == id }

    fun figureFile(deckId: String, figure: Figure) = File(dir(deckId), figure.file)

    fun subjects(): List<String> = _decks.value.map { it.subject }.filter { it.isNotBlank() }.distinct()

    @Synchronized
    fun save(deck: Deck) {
        File(dir(deck.id), "deck.json").writeText(deck.toJson().toString())
        val others = _decks.value.filter { it.id != deck.id }
        _decks.value = (others + deck).sortedByDescending { it.created }
        _version.value++
    }

    fun progress(id: String): Progress = progress.getOrPut(id) { Progress() }

    /** Call after changing a set's progress: redraws screens and saves in the background. */
    fun progressChanged(id: String) {
        val p = progress(id)
        p.lastStudied = System.currentTimeMillis()
        _version.value++
        val json = synchronized(p) { p.toJson().toString() }
        io.launch {
            try {
                File(dir(id), "progress.json").writeText(json)
            } catch (e: Exception) {
                Log.w(TAG, "couldn't save progress", e)
            }
        }
    }

    fun resetProgress(id: String) {
        progress[id] = Progress()
        progressChanged(id)
    }

    fun rename(id: String, title: String, subject: String) {
        val d = deck(id) ?: return
        save(d.copy(title = title.trim().ifBlank { d.title }, subject = subject.trim()))
    }

    fun delete(id: String) {
        _decks.value = _decks.value.filter { it.id != id }
        progress.remove(id)
        _version.value++
        io.launch { File(root, id).deleteRecursively() }
    }
}
