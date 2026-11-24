package com.example.message.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface RecycleBinDao {
    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    LiveData<List<RecycleBinEntity>> getAllLive();

    @Query("SELECT * FROM recycle_bin ORDER BY deletedAt DESC")
    List<RecycleBinEntity> getAllSync();

    @Query("SELECT EXISTS(SELECT 1 FROM recycle_bin WHERE threadId = :threadId)")
    boolean isInRecycleBinSync(long threadId);

    @Query("SELECT threadId FROM recycle_bin")
    List<Long> getRecycleBinThreadIdsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RecycleBinEntity entity);

    @Query("DELETE FROM recycle_bin WHERE threadId = :threadId")
    int deleteByThreadId(long threadId);

    @Query("DELETE FROM recycle_bin")
    int deleteAll();
}
