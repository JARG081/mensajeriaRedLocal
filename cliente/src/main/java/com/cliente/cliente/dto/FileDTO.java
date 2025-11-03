package com.cliente.cliente.dto;

public class FileDTO {
    private String sender;
    private String filename;
    private byte[] content;
    private long timestamp;

    public FileDTO() {}

    public FileDTO(String sender, String filename, byte[] content, long timestamp) {
        this.sender = sender;
        this.filename = filename;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
