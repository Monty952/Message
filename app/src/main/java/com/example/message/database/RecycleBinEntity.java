package com.example.message.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "recycle_bin")
public class RecycleBinEntity {
    @PrimaryKey
    private long threadId;
    private String address;
    private String name;
    private String snippet;
    private long date;
    private long deletedAt;

    public RecycleBinEntity(long threadId, String address, String name, String snippet, long date, long deletedAt) {
        this.threadId = threadId;
        this.address = address;
        this.name = name;
        this.snippet = snippet;
        this.date = date;
        this.deletedAt = deletedAt;
    }

    @Ignore
    public RecycleBinEntity(long threadId, String address, String snippet, long deletedAt) {
        this(threadId, address, address, snippet, System.currentTimeMillis(), deletedAt);
    }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public long getDeletedAt() { return deletedAt; }
    public void setDeletedAt(long deletedAt) { this.deletedAt = deletedAt; }
}
