package com.example.message.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "blocked_numbers")
public class BlockedNumberEntity {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String number;
    private String name;
    private long blockedAt;

    public BlockedNumberEntity(String number, String name, long blockedAt) {
        this.number = number;
        this.name = name;
        this.blockedAt = blockedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getName() { return name; }
    public String getContactName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getBlockedAt() { return blockedAt; }
    public void setBlockedAt(long blockedAt) { this.blockedAt = blockedAt; }
}
