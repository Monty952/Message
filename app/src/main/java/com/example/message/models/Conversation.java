package com.example.message.models;

public class Conversation {
    private long threadId;
    private String address;
    private String name;
    private String snippet;
    private long date;
    private boolean read;
    private int messageCount;
    private String photoUri;
    private boolean selected;

    public Conversation(long threadId, String address, String name, String snippet, long date, boolean read, int messageCount, String photoUri) {
        this.threadId = threadId;
        this.address = address;
        this.name = name;
        this.snippet = snippet;
        this.date = date;
        this.read = read;
        this.messageCount = messageCount;
        this.photoUri = photoUri;
        this.selected = false;
    }

    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getName() { return (name != null && !name.trim().isEmpty()) ? name : address; }
    public String getContactName() { return getName(); }
    public void setName(String name) { this.name = name; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public boolean isRead() { return read; }
    public int getUnreadCount() { return isRead() ? 0 : 1; }
    public void setRead(boolean read) { this.read = read; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public String getPhotoUri() { return photoUri; }
    public String getContactPhotoUri() { return getPhotoUri(); }
    public void setPhotoUri(String photoUri) { this.photoUri = photoUri; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
}
