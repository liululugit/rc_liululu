package com.example.controller;

import com.example.model.HttpRequest;
import com.example.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 接口1：接受请求并持久化
     */
    @PostMapping("/receive")
    public Map<String, Object> receiveRequest(@RequestBody HttpRequest request) {
        notificationService.receiveRequest(request);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "received");
        return result;
    }

    /**
     * 接口2：发送请求
     */
    @PostMapping("/send")
    public Map<String, Object> sendRequest(@RequestParam String requestId) {
        notificationService.sendRequest(requestId);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "sent");
        return result;
    }

    /**
     * 接口3：回调处理
     */
    @PostMapping("/callback")
    public Map<String, Object> handleCallback(@RequestParam String requestId, @RequestParam boolean success) {
        notificationService.handleCallback(requestId, success);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "callback processed");
        return result;
    }
}
