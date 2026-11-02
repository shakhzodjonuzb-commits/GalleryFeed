package uz.ilhomvich.galleryfeed.data

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

@Entity(tableName = "media_reactions")
data class MediaReaction(
    @PrimaryKey @androidx.room.ColumnInfo(name = "media_key") val mediaKey: String,
    val liked: Boolean = false,
    val bookmarked: Boolean = false,
    @androidx.room.ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "media_comments",
    indices = [Index(value = ["media_key", "created_at"])],
)
data class MediaComment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @androidx.room.ColumnInfo(name = "media_key") val mediaKey: String,
    val text: String,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Dao
interface GalleryDao {
    @Query("SELECT * FROM media_reactions")
    fun observeReactions(): Flow<List<MediaReaction>>

    @Query("SELECT * FROM media_comments WHERE media_key = :mediaKey ORDER BY created_at DESC")
    fun observeComments(mediaKey: String): Flow<List<MediaComment>>

    @Query("SELECT COUNT(*) FROM media_comments")
    fun observeCommentCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReaction(reaction: MediaReaction)

    @Insert
    suspend fun addComment(comment: MediaComment)

    @Query("DELETE FROM media_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: Long)
}

@Database(
    entities = [MediaReaction::class, MediaComment::class],
    version = 1,
    exportSchema = false,
)
abstract class GalleryDatabase : RoomDatabase() {
    abstract fun galleryDao(): GalleryDao

    companion object {
        @Volatile private var INSTANCE: GalleryDatabase? = null

        fun get(context: Context): GalleryDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                GalleryDatabase::class.java,
                "galleryfeed.db",
            ).build().also { INSTANCE = it }
        }
    }
}
