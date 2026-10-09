package com.ondetv.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "services")
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val m3uUrl: String = "",
    val active: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories", primaryKeys = ["serviceId", "kind", "categoryId"])
data class CategoryEntity(
    val serviceId: Long,
    val kind: String,
    val categoryId: String,
    val name: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "media_items",
    primaryKeys = ["serviceId", "kind", "streamId"],
    indices = [Index(value = ["serviceId", "kind", "categoryId"]), Index(value = ["name"])]
)
data class MediaEntity(
    val serviceId: Long,
    val kind: String,
    val streamId: String,
    val categoryId: String,
    val name: String,
    val logo: String? = null,
    val containerExtension: String? = null,
    val plot: String? = null,
    val directSource: String? = null,
    val favorite: Boolean = false,
    val lastPositionMs: Long = 0,
    val lastPlayedAt: Long = 0
)

@Dao
interface IptvDao {
    @Query("SELECT * FROM services ORDER BY active DESC, name")
    fun services(): Flow<List<ServiceEntity>>

    @Query("SELECT * FROM services WHERE active = 1 LIMIT 1")
    suspend fun activeService(): ServiceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertService(service: ServiceEntity): Long

    @Query("UPDATE services SET active = CASE WHEN id = :id THEN 1 ELSE 0 END")
    suspend fun setActive(id: Long)

    @Query("SELECT * FROM categories WHERE serviceId = :serviceId AND kind = :kind ORDER BY sortOrder, name")
    fun categories(serviceId: Long, kind: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM media_items WHERE serviceId = :serviceId AND kind = :kind AND categoryId = :categoryId ORDER BY name")
    fun mediaByCategory(serviceId: Long, kind: String, categoryId: String): Flow<List<MediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(items: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(items: List<MediaEntity>)

    @Query("DELETE FROM categories WHERE serviceId = :serviceId AND kind = :kind")
    suspend fun deleteCategories(serviceId: Long, kind: String)

    @Query("DELETE FROM media_items WHERE serviceId = :serviceId AND kind = :kind")
    suspend fun deleteMedia(serviceId: Long, kind: String)
}

@Database(
    entities = [ServiceEntity::class, CategoryEntity::class, MediaEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): IptvDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "onde-tv.db"
            ).build().also { instance = it }
        }
    }
}
