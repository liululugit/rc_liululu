package com.example.storage;

import com.example.model.RequestInfo;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestStorage {

    private final ConcurrentHashMap<String, RequestInfo> requestMap = new ConcurrentHashMap<>();

    public void put(String requestId, RequestInfo info) {
        requestMap.put(requestId, info);
    }

    public RequestInfo get(String requestId) {
        return requestMap.get(requestId);
    }

    public RequestInfo remove(String requestId) {
        return requestMap.remove(requestId);
    }

    public boolean containsKey(String requestId) {
        return requestMap.containsKey(requestId);
    }

    public int size() {
        return requestMap.size();
    }
}
