package com.task05;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

public class Event {
    private String id = UUID.randomUUID().toString();
    private int principalId;
    private String createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME); //ISO 8601 timestamp
    private Map<String, String> body;

    public Event(int principalId, Map<String, String> body) {
        this.principalId = principalId;
        this.body = body;
    }

    public int getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(int principalId) {
        this.principalId = principalId;
    }

    public Map<String, String> getBody() {
        return body;
    }

    public void setBody(Map<String, String> body) {
        this.body = body;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
