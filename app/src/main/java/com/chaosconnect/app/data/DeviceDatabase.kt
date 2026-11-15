package com.chaosconnect.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

// A real, initially empty local catalog. Connection transports will be added later.
@Entity(tableName = "devices")
data class SavedDevice(@PrimaryKey val id: String, val name: String, val addedAt: Long)

@Dao
interface DeviceDao {
    @Upsert
    suspend fun upsert(device:SavedDevice)
    @Query("SELECT * FROM devices ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<SavedDevice>>
}

@Database(entities = [SavedDevice::class], version = 1, exportSchema = true)
abstract class DeviceDatabase : RoomDatabase() {
    abstract fun devices(): DeviceDao
    companion object {
        fun create(context: Context) = Room.databaseBuilder(
            context, DeviceDatabase::class.java, "chaosconnect.db"
        ).build()
    }
}
