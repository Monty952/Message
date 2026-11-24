package com.example.message.database;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "scheduled_messages")
public class ScheduledMessageEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String address;
    private String name;
    private String message;
    private long scheduledTimestamp;
    private String status; // PENDING, SENT, FAILED, CANCELLED
    private String workId;

    public ScheduledMessageEntity(String address, String name, String message, long scheduledTimestamp, String status, String workId) {
        this.address = address;
        this.name = name;
        this.message = message;
        this.scheduledTimestamp = scheduledTimestamp;
        this.status = status;
        this.workId = workId;
    }

    @Ignore
    public ScheduledMessageEntity(String address, String name, String message, long scheduledTimestamp) {
        this(address, name, message, scheduledTimestamp, "PENDING", null);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public long getScheduledTimestamp() { return scheduledTimestamp; }
    public void setScheduledTimestamp(long scheduledTimestamp) { this.scheduledTimestamp = scheduledTimestamp; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getWorkId() { return workId; }
    public void setWorkId(String workId) { this.workId = workId; }
}
