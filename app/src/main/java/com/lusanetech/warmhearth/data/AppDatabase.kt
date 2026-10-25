package com.lusanetech.warmhearth.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY position, name")
    fun observeAll(): Flow<List<FavoriteContact>>

    @Query("SELECT * FROM contacts ORDER BY position, name")
    suspend fun getAll(): List<FavoriteContact>

    @Query("SELECT * FROM contacts WHERE isEmergency = 1 ORDER BY position, name")
    suspend fun emergencyContacts(): List<FavoriteContact>

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(contact: FavoriteContact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contacts WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)
}

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY name")
    fun observeAll(): Flow<List<Medicine>>

    @Query("SELECT * FROM medicines WHERE active = 1")
    suspend fun getActive(): List<Medicine>

    @Query("SELECT * FROM medicines WHERE id = :id")
    suspend fun get(id: String): Medicine?

    @Upsert
    suspend fun upsert(medicine: Medicine)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT id FROM medicines")
    suspend fun allIds(): List<String>

    @Query("DELETE FROM medicines WHERE id NOT IN (:keep)")
    suspend fun deleteAllExcept(keep: List<String>)
}

@Dao
interface DoseLogDao {
    @Query("SELECT * FROM dose_logs WHERE slot LIKE :datePrefix || '%'")
    fun observeForDay(datePrefix: String): Flow<List<DoseLog>>

    @Query("SELECT COUNT(*) FROM dose_logs WHERE medicineId = :medicineId AND slot = :slot")
    suspend fun countTaken(medicineId: String, slot: String): Int

    @androidx.room.Insert
    suspend fun insert(log: DoseLog)
}

@Database(entities = [FavoriteContact::class, Medicine::class, DoseLog::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao
    abstract fun medicines(): MedicineDao
    abstract fun doseLogs(): DoseLogDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "warmhearth.db").build()
    }
}
