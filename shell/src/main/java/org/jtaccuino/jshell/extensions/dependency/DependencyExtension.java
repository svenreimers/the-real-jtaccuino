/*
 * Copyright 2024-2025 JTaccuino Contributors
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
package org.jtaccuino.jshell.extensions.dependency;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jdk.jshell.JShell;
import org.jtaccuino.jshell.ReactiveJShell;
import org.jtaccuino.jshell.extensions.JShellExtension;

public class DependencyExtension implements JShellExtension {

    private final ReactiveJShell reactiveJShell;

    private final List<Path> paths = new ArrayList<>();

    @Descriptor(mode = Mode.SYSTEM, type = DependencyExtension.class)
    public static class Factory implements JShellExtension.Factory {

        @Override
        public DependencyExtension createExtension(ReactiveJShell jshell) {
            return new DependencyExtension(jshell);
        }
    }

    private DependencyExtension(ReactiveJShell reactiveJShell) {
        this.reactiveJShell = reactiveJShell;
    }

    // This should end in the following code injected into the shell - hopefully not too much overhead (injected once and never changed)
    // var dependencyManager = ExtensionManager.lookup(DependencyExtension.class,_$jsci$uuid);

    @Override
    public Optional<String> shellVariableName() {
        return Optional.of("dependencyManager");
    }

    @Override
    public Optional<String> initCodeSnippet() {
        return Optional.of("""
            public void addDependency(String mavenCoordinate) {
                dependencyManager.resolve(mavenCoordinate);
            }""");
    }

    public void resolve(String mavenCoordinates) {
        try {
            JShell jshell = reactiveJShell.getWrappedShell();
            if (null == jshell) {
                System.out.println("JShell is null");
            } else {
                System.out.println("Adding deps for " + mavenCoordinates);
                Dependencies.resolve(mavenCoordinates).stream()
                        .filter(p -> !paths.contains(p))
                        .filter(p -> null != p)
                        .peek(System.out::println)
                        .forEach(p -> {
                            jshell.addToClasspath(p.toString());
                            paths.add(p);
                        });
            }
        } catch (Throwable t) {
            Logger.getLogger(DependencyExtension.class.getName()).log(Level.SEVERE, null, t);
        }
    }
}
