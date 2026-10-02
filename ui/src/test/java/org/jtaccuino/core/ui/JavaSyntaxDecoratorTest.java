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
package org.jtaccuino.core.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import org.jtaccuino.jshell.ReactiveJShell;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks that the decorator asks for the shell on every dispatch instead of holding on
 * to one instance.
 *
 * <p>{@code Sheet.resetAndExecute} shuts the current shell down and installs a new
 * one. A decorator that captured the shell when the cell skin was built kept using the
 * shut-down instance for every later debounce cycle, so keyword highlighting froze at
 * its last value and static error analysis stopped for that cell.
 */
class JavaSyntaxDecoratorTest {

    private static final String TYPE_ERROR = "int value = \"not an int\";";

    private record Fixture(CodeArea area, JavaSyntaxDecorator decorator) {
    }

    @BeforeAll
    static void startToolkit() {
        FxTestRuntime.start();
    }

    @Test
    void staticAnalysisResumesAfterTheShellIsReplaced() throws InterruptedException {
        var live = new AtomicReference<ReactiveJShell>(ReactiveJShell.create(UUID.randomUUID()));
        live.get().markUserCodeExecuted();
        var fixture = createFixture(live::get);

        setText(fixture.area(), TYPE_ERROR);
        fixture.decorator().analyzeNow();
        awaitError(fixture.decorator(), "the initial shell should report the type error");

        var replaced = live.getAndSet(null);
        replaced.shutdown();
        live.set(liveShell());

        setText(fixture.area(), TYPE_ERROR + "int other = \"also wrong\";");
        fixture.decorator().analyzeNow();
        awaitError(fixture.decorator(), "static analysis must recover once a live shell is installed again");

        live.get().shutdown();
    }

    @Test
    void aShutDownShellReportsNoAnalysisInsteadOfThrowing() throws InterruptedException {
        var live = new AtomicReference<ReactiveJShell>(ReactiveJShell.create(UUID.randomUUID()));
        var shell = live.get();
        shell.shutdown();
        var fixture = createFixture(live::get);

        setText(fixture.area(), TYPE_ERROR);
        fixture.decorator().analyzeNow();
        pumpFx();
        pumpFx();

        assertFalse(hasAnyError(fixture.decorator()), "a shut-down shell yields no analysis, not a fabricated error");
    }

    private static ReactiveJShell liveShell() {
        var shell = ReactiveJShell.create(UUID.randomUUID());
        shell.markUserCodeExecuted();
        return shell;
    }

    private static Fixture createFixture(Supplier<ReactiveJShell> shellSupplier) throws InterruptedException {
        return onFx(() -> {
            var area = new CodeArea();
            var decorator = new JavaSyntaxDecorator(shellSupplier, Font.getDefault());
            area.setSyntaxDecorator(decorator);
            return new Fixture(area, decorator);
        });
    }

    private static void setText(CodeArea area, String text) throws InterruptedException {
        onFx(() -> {
            area.replaceText(TextPos.ZERO, area.getModel().getDocumentEnd(), text);
            return null;
        });
    }

    private static <T> T onFx(Supplier<T> task) throws InterruptedException {
        var holder = new AtomicReference<T>();
        var thrown = new AtomicReference<Throwable>();
        var latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                holder.set(task.get());
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(30, TimeUnit.SECONDS)) {
            fail("an FX task did not complete");
        }
        if (null != thrown.get()) {
            fail("an FX task threw", thrown.get());
        }
        return holder.get();
    }

    private static void pumpFx() throws InterruptedException {
        onFx(() -> null);
    }

    private static boolean hasAnyError(JavaSyntaxDecorator decorator) {
        for (var offset = 0; offset < 4096; offset++) {
            if (decorator.errorAt(offset).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static void awaitError(JavaSyntaxDecorator decorator, String message) throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            pumpFx();
            if (hasAnyError(decorator)) {
                return;
            }
            Thread.sleep(50);
        }
        fail(message);
    }
}
