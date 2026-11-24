package com.example.message.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ArchiveDao {
    @Query("SELECT * FROM archived_conversations ORDER BY archivedAt DESC")
    LiveData<List<ArchiveEntity>> getAllLive();

    @Query("SELECT * FROM archived_conversations")
    List<ArchiveEntity> getAllSync();

    @Query("SELECT EXISTS(SELECT 1 FROM archived_conversations WHERE threadId = :threadId)")
    boolean isArchivedSync(long threadId);

    @Query("SELECT threadId FROM archived_conversations")
    List<Long> getArchivedThreadIdsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ArchiveEntity entity);

    @Query("DELETE FROM archived_conversations WHERE threadId = :threadId")
    int deleteByThreadId(long threadId);
}
