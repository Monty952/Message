package com.example.message.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    LiveData<List<BlockedNumberEntity>> getAllLive();

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAt DESC")
    List<BlockedNumberEntity> getAllSync();

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number)")
    boolean isBlockedSync(String number);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BlockedNumberEntity entity);

    @androidx.room.Delete
    void delete(BlockedNumberEntity entity);

    @Query("DELETE FROM blocked_numbers WHERE number = :number")
    int deleteByNumber(String number);

    @Query("DELETE FROM blocked_numbers WHERE id = :id")
    int deleteById(long id);
}
