package com.goalio.scores

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

@Entity(tableName = "translation_cache", indices = [Index(value = ["hash"], unique = true)])
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hash: String,
    val original_text: String,
    val translated_text: String,
    val target_language: String,
    val created_at: Long,
    val updated_at: Long
)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE hash = :hash LIMIT 1")
    suspend fun find(hash: String): TranslationCacheEntity?

    @Query("SELECT * FROM translation_cache")
    suspend fun all(): List<TranslationCacheEntity>

    @Query("DELETE FROM translation_cache WHERE original_text = translated_text")
    suspend fun deleteNoOpTranslations()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(entity: TranslationCacheEntity)
}

@Database(entities = [TranslationCacheEntity::class], version = 1, exportSchema = false)
abstract class TranslationDatabase : RoomDatabase() {
    abstract fun translationCacheDao(): TranslationCacheDao

    companion object {
        @Volatile private var instance: TranslationDatabase? = null

        fun get(context: Context): TranslationDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TranslationDatabase::class.java,
                "goalio_translation_cache.db"
            ).build().also { instance = it }
        }
    }
}
