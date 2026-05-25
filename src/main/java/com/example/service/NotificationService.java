package com.example.service;

import com.example.exception.RequestNotFoundException;
import com.example.model.HttpRequest;
import com.example.model.RequestInfo;
import com.example.model.RequestStatus;
import com.example.pool.ThreadPoolRouter;
import com.example.sender.HttpSender;
import com.example.storage.RequestStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_RETRY = 10;

    private final RequestStorage requestStorage;
    private final ThreadPoolRouter threadPoolRouter;
    private final HttpSender httpSender;

    public NotificationService(RequestStorage requestStorage, ThreadPoolRouter threadPoolRouter, HttpSender httpSender) {
        this.requestStorage = requestStorage;
        this.threadPoolRouter = threadPoolRouter;
        this.httpSender = httpSender;
    }

    /**
     * 接口1：接受业务系统的通知请求并持久化到内存
     */
    public void receiveRequest(HttpRequest request) {
        String requestId = request.getRequestId();
        if (requestStorage.containsKey(requestId)) {
            log.info("Duplicate request ignored: {}", requestId);
            return;
        }

        RequestInfo info = new RequestInfo();
        info.setRequest(request);
        info.setRetryCount(0);
        info.setCreateTime(System.currentTimeMillis());
        info.setStatus(RequestStatus.PENDING);

        requestStorage.put(requestId, info);
        log.info("Request received and stored: {}", requestId);
    }

    /**
     * 接口2：根据 requestId 发送 HTTP 通知请求
     */
    public void sendRequest(String requestId) {
        RequestInfo info = requestStorage.get(requestId);
        if (info == null) {
            throw new RequestNotFoundException(requestId);
        }

        info.setStatus(RequestStatus.SENDING);
        info.setLastRetryTime(System.currentTimeMillis());

        threadPoolRouter.getPool(info.getRequest().getSupplierId()).execute(() -> {
            info.setStatus(RequestStatus.WAITING_ACK);
            boolean success = httpSender.send(info.getRequest());
            handleCallback(requestId, success);
        });
    }

    /**
     * 接口3：接收外部服务方的回调通知，处理成功/失败逻辑
     */
    public void handleCallback(String requestId, boolean success) {
        if (success) {
            requestStorage.remove(requestId);
            log.info("Request succeeded, removed from storage: {}", requestId);
            return;
        }

        RequestInfo info = requestStorage.get(requestId);
        if (info == null) {
            throw new RequestNotFoundException(requestId);
        }

        if (info.getRetryCount() >= MAX_RETRY) {
            info.setStatus(RequestStatus.FAILED);
            requestStorage.remove(requestId);
            log.error("Request reached max retries, moved to dead letter: {}", requestId);
            return;
        }

        info.setRetryCount(info.getRetryCount() + 1);
        info.setStatus(RequestStatus.PENDING);
        log.info("Request retry {}/{}: {}", info.getRetryCount(), MAX_RETRY, requestId);
        sendRequest(requestId);
    }
}
