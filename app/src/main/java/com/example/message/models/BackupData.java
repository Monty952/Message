package com.example.message.models;

import java.util.List;

public class BackupData {
    private long backupTimestamp;
    private String appVersion;
    private int totalMessages;
    private List<MessageItem> messages;

    public BackupData() {}

    public BackupData(long backupTimestamp, String appVersion, int totalMessages, List<MessageItem> messages) {
        this.backupTimestamp = backupTimestamp;
        this.appVersion = appVersion;
        this.totalMessages = totalMessages;
        this.messages = messages;
    }

    public long getBackupTimestamp() { return backupTimestamp; }
    public void setBackupTimestamp(long backupTimestamp) { this.backupTimestamp = backupTimestamp; }
    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }
    public int getTotalMessages() { return totalMessages; }
    public void setTotalMessages(int totalMessages) { this.totalMessages = totalMessages; }
    public List<MessageItem> getMessages() { return messages; }
    public void setMessages(List<MessageItem> messages) { this.messages = messages; }
}
