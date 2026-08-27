package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PrintJobDao {
    @Query("SELECT * FROM print_jobs ORDER BY receivedTimestamp DESC")
    fun getAllJobs(): Flow<List<PrintJobEntity>>

    @Query("SELECT * FROM print_jobs ORDER BY receivedTimestamp DESC")
    suspend fun getAllJobsList(): List<PrintJobEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: PrintJobEntity): Long

    @Update
    suspend fun updateJob(job: PrintJobEntity)

    @Delete
    suspend fun deleteJob(job: PrintJobEntity)

    @Query("DELETE FROM print_jobs")
    suspend fun deleteAllJobs()

    @Query("SELECT COUNT(*) FROM print_jobs")
    fun getJobCount(): Flow<Int>
}
