package com.example.message.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {
        BlockedNumberEntity.class,
        ArchiveEntity.class,
        RecycleBinEntity.class,
        ScheduledMessageEntity.class
}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract BlockedNumberDao blockedNumberDao();
    public abstract ArchiveDao archiveDao();
    public abstract RecycleBinDao recycleBinDao();
    public abstract ScheduledMessageDao scheduledMessageDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "messages_app.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
        return INSTANCE;
    }
}
