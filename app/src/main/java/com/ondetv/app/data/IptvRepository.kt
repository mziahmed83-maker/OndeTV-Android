package com.ondetv.app.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

private interface XtreamApi {
    @GET("player_api.php")
    suspend fun auth(
        @Query("username") username: String,
        @Query("password") password: String
    ): JsonObject

    @GET("player_api.php")
    suspend fun list(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): JsonArray

    @GET("player_api.php")
    suspend fun seriesInfo(
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_info",
        @Query("series_id") seriesId: String
    ): JsonObject
}

class IptvRepository(private val db: AppDatabase) {
    val dao: IptvDao = db.dao()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun addXtream(name: String, baseUrl: String, username: String, password: String): Result<Long> = runCatching {
        val normalized = normalizeBase(baseUrl)
        val api = api(normalized)
        val auth = api.auth(username, password)
        val userInfo = auth.getAsJsonObject("user_info")
        val status = userInfo?.get("auth")?.asInt ?: 0
        require(status == 1) { "Authentification Xtream refusée" }
        val id = dao.upsertService(
            ServiceEntity(name = name, type = "xtream", baseUrl = normalized, username = username, password = password)
        )
        dao.setActive(id)
        refresh(id)
        id
    }

    suspend fun addM3u(name: String, url: String): Result<Long> = runCatching {
        require(url.startsWith("http://") || url.startsWith("https://")) { "URL M3U invalide" }
        val id = dao.upsertService(ServiceEntity(name = name, type = "m3u", m3uUrl = url))
        dao.setActive(id)
        refresh(id)
        id
    }

    suspend fun refresh(serviceId: Long) {
        val service = dao.activeService()?.takeIf { it.id == serviceId }
            ?: error("Service IPTV introuvable")
        if (service.type == "xtream") refreshXtream(service) else refreshM3u(service)
    }

    suspend fun getSeriesEpisodes(service: ServiceEntity, seriesId: String): List<MediaEntity> {
        if (service.type != "xtream") return emptyList()
        val root = api(service.baseUrl).seriesInfo(service.username, service.password, seriesId = seriesId)
        val episodes = root.getAsJsonObject("episodes") ?: return emptyList()
        val result = mutableListOf<MediaEntity>()
        episodes.entrySet()
            .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
            .forEach { (seasonKey, value) ->
                if (!value.isJsonArray) return@forEach
                value.asJsonArray.forEach { element ->
                    if (!element.isJsonObject) return@forEach
                    val o = element.asJsonObject
                    val id = o.string("id")
                    if (id.isBlank()) return@forEach
                    val episodeNum = o.string("episode_num")
                    val title = o.string("title").ifBlank {
                        if (episodeNum.isNotBlank()) "Épisode $episodeNum" else "Épisode"
                    }
                    val info = o.getAsJsonObject("info")
                    result += MediaEntity(
                        serviceId = service.id,
                        kind = "series",
                        streamId = id,
                        categoryId = seriesId,
                        name = "S$seasonKey · $title",
                        logo = info?.string("movie_image")?.ifBlank { null },
                        containerExtension = o.string("container_extension").ifBlank { "mp4" },
                        plot = info?.string("plot")?.ifBlank { null }
                    )
                }
            }
        return result
    }

    private suspend fun refreshXtream(service: ServiceEntity) {
        val api = api(service.baseUrl)
        val u = service.username
        val p = service.password
        refreshKind(service, "live", api.list(u, p, "get_live_categories"), api.list(u, p, "get_live_streams"))
        refreshKind(service, "vod", api.list(u, p, "get_vod_categories"), api.list(u, p, "get_vod_streams"))
        refreshKind(service, "series", api.list(u, p, "get_series_categories"), api.list(u, p, "get_series"))
    }

    private suspend fun refreshKind(service: ServiceEntity, kind: String, categoryJson: JsonArray, mediaJson: JsonArray) {
        val categories = categoryJson.mapIndexed { index, element ->
            val o = element.asJsonObject
            CategoryEntity(
                serviceId = service.id,
                kind = kind,
                categoryId = o.string("category_id"),
                name = o.string("category_name").ifBlank { "Sans nom" },
                sortOrder = index
            )
        }
        val media = mediaJson.mapNotNull { element ->
            val o = element.asJsonObject
            val streamId = when (kind) {
                "series" -> o.string("series_id")
                else -> o.string("stream_id")
            }
            if (streamId.isBlank()) return@mapNotNull null
            MediaEntity(
                serviceId = service.id,
                kind = kind,
                streamId = streamId,
                categoryId = o.string("category_id"),
                name = o.string("name").ifBlank { "Sans nom" },
                logo = o.string("stream_icon").ifBlank { o.string("cover") }.ifBlank { null },
                containerExtension = o.string("container_extension").ifBlank { null },
                plot = o.string("plot").ifBlank { null },
                directSource = o.string("direct_source").ifBlank { null }
            )
        }
        dao.deleteCategories(service.id, kind)
        dao.deleteMedia(service.id, kind)
        if (categories.isNotEmpty()) dao.upsertCategories(categories)
        if (media.isNotEmpty()) dao.upsertMedia(media)
    }

    private suspend fun refreshM3u(service: ServiceEntity) = withContext(Dispatchers.IO) {
        val response = http.newCall(Request.Builder().url(service.m3uUrl).build()).execute()
        response.use {
            require(it.isSuccessful) { "Erreur M3U HTTP ${it.code}" }
            val text = it.body?.string().orEmpty()
            require(text.isNotBlank()) { "Playlist M3U vide" }
            val parsed = M3uParser.parse(service.id, text)
            dao.deleteCategories(service.id, "live")
            dao.deleteMedia(service.id, "live")
            if (parsed.first.isNotEmpty()) dao.upsertCategories(parsed.first)
            if (parsed.second.isNotEmpty()) dao.upsertMedia(parsed.second)
        }
    }

    fun streamUrl(service: ServiceEntity, item: MediaEntity): String {
        item.directSource?.takeIf { it.isNotBlank() }?.let { return it }
        if (service.type == "m3u") return item.streamId
        val ext = item.containerExtension?.ifBlank { null } ?: if (item.kind == "live") "ts" else "mp4"
        val folder = when (item.kind) {
            "vod" -> "movie"
            "series" -> "series"
            else -> "live"
        }
        return "${service.baseUrl}$folder/${service.username}/${service.password}/${item.streamId}.$ext"
    }

    private fun api(baseUrl: String): XtreamApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(http)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(XtreamApi::class.java)

    private fun normalizeBase(input: String): String {
        var value = input.trim()
        require(value.startsWith("http://") || value.startsWith("https://")) { "L'adresse doit commencer par http:// ou https://" }
        if (!value.endsWith('/')) value += '/'
        return value
    }
}

private fun JsonObject.string(key: String): String =
    get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

object M3uParser {
    fun parse(serviceId: Long, text: String): Pair<List<CategoryEntity>, List<MediaEntity>> {
        val categories = linkedMapOf<String, String>()
        val media = mutableListOf<MediaEntity>()
        var name = "Chaîne"
        var group = "Autres"
        var logo: String? = null
        text.lineSequence().map { it.trim() }.forEach { line ->
            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                name = line.substringAfterLast(',').trim().ifBlank { "Chaîne" }
                group = attribute(line, "group-title").ifBlank { "Autres" }
                logo = attribute(line, "tvg-logo").ifBlank { null }
            } else if (line.isNotBlank() && !line.startsWith('#')) {
                val categoryId = slug(group)
                categories[categoryId] = group
                media += MediaEntity(
                    serviceId = serviceId,
                    kind = "live",
                    streamId = line,
                    categoryId = categoryId,
                    name = name,
                    logo = logo,
                    directSource = line
                )
            }
        }
        val cats = categories.entries.mapIndexed { index, e ->
            CategoryEntity(serviceId, "live", e.key, e.value, index)
        }
        return cats to media
    }

    private fun attribute(line: String, key: String): String {
        val token = "$key=\""
        return line.substringAfter(token, "").substringBefore('"', "")
    }

    private fun slug(value: String): String = value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "autres" }
}
