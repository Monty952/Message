package com.example.message.models;

import com.example.message.utils.OtpHelper;

public class MessageItem {
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;

    private long id;
    private long threadId;
    private String address;
    private String body;
    private long date;
    private int type;
    private int status;
    private boolean read;
    private boolean selected;
    private String otpCode;
    // True when the message comes from the MMS table. MMS and SMS ids are separate sequences, so this tells them
    // apart (e.g. for delete). transient: backups stay unchanged (they contain SMS only).
    private transient boolean mms;

    // MMS attachment details (transient to keep backup serialization unchanged)
    private transient String attachmentUri;
    private transient String attachmentType;
    private transient String subject;
    private transient boolean downloadPending;

    public MessageItem(long id, long threadId, String address, String body, long date, int type, int status, boolean read) {
        this.id = id;
        this.threadId = threadId;
        this.address = address;
        this.body = body;
        this.date = date;
        this.type = type;
        this.status = status;
        this.read = read;
        this.selected = false;
        this.otpCode = extractOtp(body);
    }

    private String extractOtp(String text) {
        return OtpHelper.extractOtp(text);
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getThreadId() { return threadId; }
    public void setThreadId(long threadId) { this.threadId = threadId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getBody() { return body; }
    public void setBody(String body) {
        this.body = body;
        this.otpCode = extractOtp(body);
    }
    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
    public int getType() { return type; }
    public boolean isIncoming() { return type == TYPE_INCOMING; }
    public boolean isDateHeader() { return type == 0; }
    public void setType(int type) { this.type = type; }
    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isMms() { return mms; }
    public void setMms(boolean mms) { this.mms = mms; }
    public String getOtpCode() { return otpCode; }
    public boolean hasOtp() { return otpCode != null && !otpCode.isEmpty(); }

    public String getAttachmentUri() { return attachmentUri; }
    public void setAttachmentUri(String attachmentUri) { this.attachmentUri = attachmentUri; }
    public String getAttachmentType() { return attachmentType; }
    public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
    public boolean hasAttachment() { return attachmentUri != null && !attachmentUri.trim().isEmpty(); }
    public boolean isImageAttachment() { return attachmentType != null && attachmentType.startsWith("image/"); }
    public boolean isVideoAttachment() { return attachmentType != null && attachmentType.startsWith("video/"); }
    public boolean isAudioAttachment() { return attachmentType != null && attachmentType.startsWith("audio/"); }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public boolean isDownloadPending() { return downloadPending; }
    public void setDownloadPending(boolean downloadPending) { this.downloadPending = downloadPending; }
}
