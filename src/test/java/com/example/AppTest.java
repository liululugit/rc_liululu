package com.example;

import com.example.model.HttpRequest;
import com.example.service.NotificationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
public class AppTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    public void testReceiveRequest() {
        HttpRequest req = new HttpRequest();
        req.setRequestId("test-001");
        req.setUrl("http://example.com/api");
        req.setSupplierId("supplierA");
        req.setMethod("POST");
        req.setBody("test body");

        notificationService.receiveRequest(req);
        // 重复提交应被幂等处理
        notificationService.receiveRequest(req);
    }

    @Test
    public void testHandleCallbackSuccess() {
        HttpRequest req = new HttpRequest();
        req.setRequestId("test-002");
        req.setUrl("http://example.com/api");
        req.setSupplierId("supplierA");
        req.setMethod("POST");

        notificationService.receiveRequest(req);
        notificationService.handleCallback("test-002", true);
        // 成功后应删除，再次回调应抛异常
        try {
            notificationService.handleCallback("test-002", false);
            fail("Expected exception");
        } catch (Exception e) {
            // expected
        }
    }
}
