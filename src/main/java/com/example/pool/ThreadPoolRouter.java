package com.example.pool;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

@Component
public class ThreadPoolRouter {

    private final Map<String, ThreadPoolExecutor> poolMap = new HashMap<>();

    private static final String[] SUPPLIERS = {"supplierA", "supplierB", "supplierC"};

    @PostConstruct
    public void init() {
        for (String supplierId : SUPPLIERS) {
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    4, 8, 60L, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(1000),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
            poolMap.put(supplierId, pool);
        }
    }

    public ThreadPoolExecutor getPool(String supplierId) {
        return poolMap.get(supplierId);
    }
}
