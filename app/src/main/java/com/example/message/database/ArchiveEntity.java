package com.example.message.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "archived_conversations")
public class ArchiveEntity {
    @PrimaryKey
    private long threadId;
    private String address;
    private long archivedAt;

    public ArchiveEntity(long threadId, String address, long archivedAt) {
        this.threadId = threadId;
        this.address = address;
        this.archivedAt = archivedAt;
    }

    @Ignore
    public ArchiveEntity(long threadId, String address, String snippetOrName, long archivedAt) {
        this.threadId = threadId;
        this.address = address;
        this.archivedAt = archivedAt;
    }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public long getArchivedAt() { return archivedAt; }
    public void setArchivedAt(long archivedAt) { this.archivedAt = archivedAt; }
}
