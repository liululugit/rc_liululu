package com.example.model;

public class RequestInfo {
    private HttpRequest request;
    private int retryCount;
    private long createTime;
    private long lastRetryTime;
    private RequestStatus status;

    public HttpRequest getRequest() { return request; }
    public void setRequest(HttpRequest request) { this.request = request; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getLastRetryTime() { return lastRetryTime; }
    public void setLastRetryTime(long lastRetryTime) { this.lastRetryTime = lastRetryTime; }

    public RequestStatus getStatus() { return status; }
    public void setStatus(RequestStatus status) { this.status = status; }
}
