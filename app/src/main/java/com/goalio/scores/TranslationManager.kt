package com.goalio.scores

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TranslationManager private constructor(context: Context) {
    private val dao = TranslationDatabase.get(context).translationCacheDao()
    private val memory = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = mutableMapOf<String, PendingTranslation>()
    private val active = mutableMapOf<String, PendingTranslation>()
    private val scheduledLanguages = mutableSetOf<String>()
    private val pendingMutex = Mutex()

    suspend fun warmCache() {
        dao.deleteNoOpTranslations()
        dao.all().forEach { memory[it.hash] = it.translated_text }
    }

    fun peek(text: String, targetLanguage: String): String? =
        memory[hash(text, targetLanguage)]?.takeUnless { it == text }

    suspend fun translateText(text: String, targetLanguage: String): String {
        if (isEnglish(targetLanguage) || !isTranslatable(text)) return text
        val key = hash(text, targetLanguage)
        memory[key]?.takeUnless { it == text }?.let { return it }
        dao.find(key)?.translated_text?.let {
            if (it != text) {
                memory[key] = it
                return it
            }
        }

        val deferred = pendingMutex.withLock {
            pending[key]?.deferred ?: active[key]?.deferred ?: CompletableDeferred<String>().also { created ->
                pending[key] = PendingTranslation(text, targetLanguage, created)
                if (scheduledLanguages.add(targetLanguage)) {
                    scope.launch {
                        delay(40)
                        flushPending(targetLanguage)
                    }
                }
            }
        }
        return deferred.await()
    }

    suspend fun translateBatch(texts: List<String>, targetLanguage: String): Map<String, String> {
        val unique = texts.distinct()
        if (isEnglish(targetLanguage)) return unique.associateWith { it }
        val result = linkedMapOf<String, String>()
        val missing = mutableListOf<String>()
        unique.forEach { text ->
            if (!isTranslatable(text)) {
                result[text] = text
            } else {
                val key = hash(text, targetLanguage)
                val cached = (memory[key] ?: dao.find(key)?.translated_text).takeUnless { it == text }
                if (cached != null) {
                    memory[key] = cached
                    result[text] = cached
                } else {
                    missing += text
                }
            }
        }
        val fetched = if (missing.isEmpty()) emptyMap() else runCatching {
            GoalioBackendApi.translateBatch(missing, targetLanguage)
        }.getOrNull()
        missing.forEach { original ->
            val translated = fetched?.get(original) ?: original
            if (fetched != null && translated != original) {
                save(original, translated, targetLanguage, hash(original, targetLanguage))
            }
            result[original] = translated
        }
        return result
    }

    suspend fun translateJson(json: JSONObject, targetLanguage: String): JSONObject {
        if (isEnglish(targetLanguage)) return JSONObject(json.toString())
        val texts = mutableListOf<String>()
        collectStrings(json, null, texts)
        val translated = translateBatch(texts, targetLanguage)
        return replaceStrings(json, null, translated) as JSONObject
    }

    private suspend fun save(original: String, translated: String, language: String, key: String) {
        val now = System.currentTimeMillis()
        val old = dao.find(key)
        dao.save(
            TranslationCacheEntity(
                id = old?.id ?: 0,
                hash = key,
                original_text = original,
                translated_text = translated,
                target_language = language,
                created_at = old?.created_at ?: now,
                updated_at = now
            )
        )
        memory[key] = translated
    }

    private suspend fun flushPending(language: String) {
        val requests = pendingMutex.withLock {
            val selected = pending.filterValues { it.language == language }
            selected.keys.forEach(pending::remove)
            active.putAll(selected)
            scheduledLanguages.remove(language)
            selected
        }
        if (requests.isEmpty()) return
        val originals = requests.values.map { it.original }
        val translations = runCatching {
            if (originals.size == 1) {
                originals.associateWith { GoalioBackendApi.translateText(it, language) }
            } else {
                GoalioBackendApi.translateBatch(originals, language)
            }
        }.getOrNull()
        requests.forEach { (key, request) ->
            val translated = translations?.get(request.original) ?: request.original
            if (translations != null && translated != request.original) {
                runCatching { save(request.original, translated, language, key) }
            }
            request.deferred.complete(translated)
            pendingMutex.withLock { active.remove(key) }
        }
    }

    private data class PendingTranslation(
        val original: String,
        val language: String,
        val deferred: CompletableDeferred<String>
    )

    private fun collectStrings(value: Any?, key: String?, output: MutableList<String>) {
        when (value) {
            is JSONObject -> value.keys().forEach { childKey -> collectStrings(value.opt(childKey), childKey, output) }
            is JSONArray -> for (index in 0 until value.length()) collectStrings(value.opt(index), key, output)
            is String -> if (isTranslatable(value, key)) output += value
        }
    }

    private fun replaceStrings(value: Any?, key: String?, translations: Map<String, String>): Any? = when (value) {
        is JSONObject -> JSONObject().also { output ->
            value.keys().forEach { childKey -> output.put(childKey, replaceStrings(value.opt(childKey), childKey, translations)) }
        }
        is JSONArray -> JSONArray().also { output ->
            for (index in 0 until value.length()) output.put(replaceStrings(value.opt(index), key, translations))
        }
        is String -> if (isTranslatable(value, key)) translations[value] ?: value else value
        else -> value
    }

    companion object {
        private val protectedKeys = setOf(
            "id", "uid", "url", "uri", "href", "logo", "image", "key", "code", "slug",
            "status", "state", "type", "enum", "date", "time", "kickoff", "score", "rank",
            "team", "teamname", "hometeam", "awayteam", "player", "playername", "username",
            "name", "matchid", "eventid", "sessionid", "questionid", "targetlanguage"
        )
        @Volatile private var instance: TranslationManager? = null

        fun get(context: Context): TranslationManager = instance ?: synchronized(this) {
            instance ?: TranslationManager(context.applicationContext).also { instance = it }
        }

        fun hash(originalText: String, targetLanguage: String): String {
            val bytes = "$originalText$targetLanguage".toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }

        fun isEnglish(language: String): Boolean = language.substringBefore('-').equals("en", true)

        fun isTranslatable(text: String, key: String? = null): Boolean {
            val value = text.trim()
            val normalizedKey = key.orEmpty().lowercase().filter(Char::isLetter)
            return value.isNotEmpty() &&
                normalizedKey !in protectedKeys &&
                value.any(Char::isLetter) &&
                !isTechnicalValue(value)
        }

        private fun isTechnicalValue(value: String): Boolean {
            val hasWhitespace = value.any(Char::isWhitespace)
            if (value.startsWith("http://", true) || value.startsWith("https://", true)) return true
            if (!hasWhitespace && value.contains('@') && value.substringAfter('@').contains('.')) return true
            if (value.startsWith("{") && value.endsWith("}")) return true
            if (value.startsWith("\${") && value.endsWith("}")) return true
            if (!hasWhitespace && value.startsWith('%')) return true
            if (!hasWhitespace && (value.contains('_') || value.contains('/'))) return true
            if (!hasWhitespace && value.count { it == '.' } >= 2) return true
            if (!hasWhitespace && value.any(Char::isDigit) && value.all { it.isLetterOrDigit() || it == '-' }) return true
            return false
        }
    }
}
