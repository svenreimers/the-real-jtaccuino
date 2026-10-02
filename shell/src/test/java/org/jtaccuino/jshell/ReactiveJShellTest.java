/*
 * Copyright 2026 JTaccuino Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jtaccuino.jshell;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * Checks that a shut-down shell reports "no result" instead of losing the request
 * silently or throwing out of the async entry points.
 *
 * <p>Once the worker is shut down, {@code ThreadPoolExecutor.execute} can no longer
 * run a task. With a {@code DiscardPolicy} rejection handler that submission is
 * dropped without a trace: the entry point returns normally, the consumer is never
 * called and the chained {@code exceptionally} never runs, so a caller holding a
 * stale shell sees highlighting and parse errors simply stop with nothing logged.
 *
 * <p>The default {@code AbortPolicy} instead makes {@code execute} throw
 * {@code RejectedExecutionException}, and {@code CompletableFuture.supplyAsync} calls
 * {@code execute} before it returns a future, so the throw is synchronous and would
 * escape the entry point. The dispatch helpers in {@code ReactiveJShell} convert it
 * into a failed future so it reaches {@code exceptionally} and is logged.
 */
class ReactiveJShellTest {

    private static List<LogRecord> captureRejections(Runnable action) {
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(ReactiveJShell.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            action.run();
        } finally {
            logger.removeHandler(handler);
        }
        return records;
    }

    private static boolean mentionsRejection(Throwable throwable) {
        for (var current = throwable; null != current; current = current.getCause()) {
            if (current instanceof RejectedExecutionException) {
                return true;
            }
        }
        return false;
    }

    @Test
    void deliversHighlightingWhileTheWorkerIsRunning() throws InterruptedException {
        var shell = ReactiveJShell.create(UUID.randomUUID());
        try {
            var delivered = new CountDownLatch(1);
            shell.highlightingAsync("int x = 1;", highlights -> delivered.countDown());
            assertTrue(delivered.await(30, TimeUnit.SECONDS), "highlighting should be delivered");
        } finally {
            shell.shutdown();
        }
    }

    @Test
    void asyncEntryPointsDoNotThrowAfterShutdown() {
        var shell = ReactiveJShell.create(UUID.randomUUID());
        shell.shutdown();
        var consumerCalled = new AtomicBoolean();

        assertDoesNotThrow(() -> captureRejections(() -> {
            shell.highlightingAsync("int x = 1;", result -> consumerCalled.set(true));
            shell.parseErrorsAsync("int x = 1;", result -> consumerCalled.set(true));
            shell.completionAsync("int x = ", 7, result -> consumerCalled.set(true));
            shell.documentationAsync("String", 0, result -> consumerCalled.set(true));
            shell.documentationAsyncFor(() -> "javadoc", result -> consumerCalled.set(true));
            shell.evalAsync(() -> consumerCalled.set(true), "int x = 1;", result -> consumerCalled.set(true));
        }), "a shut-down worker must not throw into the caller");

        assertFalse(consumerCalled.get(), "a rejected task must not reach the consumer or the pre-action");
    }

    @Test
    void reportsARejectionAfterShutdownInsteadOfDroppingItSilently() {
        var shell = ReactiveJShell.create(UUID.randomUUID());
        shell.shutdown();

        var records = captureRejections(() -> shell.highlightingAsync("int x = 1;", result -> {
        }));

        assertTrue(records.stream().anyMatch(record -> Level.SEVERE.equals(record.getLevel())
                && mentionsRejection(record.getThrown())),
                "a shut-down worker must be reported through exceptionally, got: "
                        + records.stream().map(record -> record.getLevel() + "/" + record.getThrown()).toList());
    }
}
