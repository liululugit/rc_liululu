package com.example.sender;

import com.example.model.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpSender {

    private static final Logger log = LoggerFactory.getLogger(HttpSender.class);
    private final RestTemplate restTemplate = new RestTemplate();

    public boolean send(HttpRequest request) {
        try {
            HttpMethod method = HttpMethod.resolve(request.getMethod() != null ? request.getMethod().toUpperCase() : "POST");
            if (method == null) {
                method = HttpMethod.POST;
            }

            HttpHeaders headers = new HttpHeaders();
            if (request.getHeaders() != null) {
                request.getHeaders().forEach(headers::add);
            }

            HttpEntity<String> entity = new HttpEntity<>(request.getBody(), headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    request.getUrl(), method, entity, String.class
            );

            log.info("HTTP {} to {} succeeded, status={}", method, request.getUrl(), response.getStatusCodeValue());
            return true;
        } catch (Exception e) {
            log.error("HTTP {} to {} failed: {}", request.getMethod(), request.getUrl(), e.getMessage());
            return false;
        }
    }
}
