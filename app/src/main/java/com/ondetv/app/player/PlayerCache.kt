package com.ondetv.app.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object PlayerCache {
    private const val MAX_CACHE_BYTES = 2L * 1024L * 1024L * 1024L

    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "onde_stream_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }

    fun dataSourceFactory(context: Context): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(get(context))
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context.applicationContext))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
