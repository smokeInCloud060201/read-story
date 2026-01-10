package com.example.readstory.common.service;

import com.example.readstory.common.dto.RetrySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.Callable;

@Service
@Slf4j
public class RetryService {

    public <T> T execute(Callable<T> task, RetrySpec spec) {
        Exception last = null;

        for (int attempt = 1; attempt <= spec.maxAttempts(); attempt++) {
            try {
                return task.call();
            } catch (Exception e) {
                last = e;

                log.warn("Retry {}/{} failed: {}", attempt, spec.maxAttempts(), e.getMessage());

                backoff(attempt, spec);
            }
        }

        throw new RuntimeException("Retry exhausted after " + spec.maxAttempts(), last);
    }

    private void backoff(int attempt, RetrySpec spec) {
        try {
            long delay = Math.min(spec.baseDelayMs() * (1L << (attempt - 1)), spec.maxDelayMs());
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}

