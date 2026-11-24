package com.example.message.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY scheduledTimestamp ASC")
    LiveData<List<ScheduledMessageEntity>> getAllLive();

    @Query("SELECT * FROM scheduled_messages WHERE status = 'PENDING' ORDER BY scheduledTimestamp ASC")
    List<ScheduledMessageEntity> getPendingSync();

    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    ScheduledMessageEntity getByIdSync(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ScheduledMessageEntity entity);

    @Update
    void update(ScheduledMessageEntity entity);

    @Query("UPDATE scheduled_messages SET status = :status WHERE id = :id")
    void updateStatus(long id, String status);

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    int deleteById(long id);
}
