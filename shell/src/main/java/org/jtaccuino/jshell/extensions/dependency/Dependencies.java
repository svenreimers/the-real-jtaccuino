/*
 * Copyright 2024 JTaccuino Contributors
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

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

final class Dependencies {

    @SuppressWarnings("deprecation")
    private static RepositorySystem newRepositorySystem() {
        var repoSup = new RepositorySystemSupplier();
        return repoSup.get();
    }

    private static RepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(System.getProperty("user.home")
                + File.separator + "jtaccuino"+  File.separator + "cache" + File.separator + "local-repo");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        return session;
    }

    static List<Path> resolve(String mavenCoordinates) {
        try {
            RepositorySystem repoSystem = newRepositorySystem();

            RepositorySystemSession session = newSession(repoSystem);

            Dependency dependency
                    = new Dependency(new DefaultArtifact(mavenCoordinates), JavaScopes.COMPILE);
            RemoteRepository central = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

            CollectRequest collectRequest = new CollectRequest();
            collectRequest.setRoot(dependency);
            collectRequest.addRepository(central);
            DependencyNode node = repoSystem.collectDependencies(session, collectRequest).getRoot();

            DependencyRequest dependencyRequest = new DependencyRequest();
            dependencyRequest.setRoot(node);

            var classpathFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
            DependencyFilter javafxFilter = (dn, list) -> {
                return !"org.openjfx".equals(dn.getArtifact().getGroupId());
            };

            var filter = DependencyFilterUtils.andFilter(classpathFilter, javafxFilter);

            dependencyRequest.setFilter(filter);

            repoSystem.resolveDependencies(session, dependencyRequest);

            var test = new DependencyVisitor() {

                int depth =0;

                @Override
                public boolean visitEnter(DependencyNode node) {
                    System.out.println(" ".repeat(depth)
                            + ":" + node.getDependency().getScope()
                            + ":" + node.getDependency().getOptional()
                            + ":" + node.getDependency().getArtifact()
                            + " - " + node.getArtifact().getGroupId()
                            + ":" + node.getArtifact().getArtifactId()
                            + " Deps: " + node.getChildren().size());
                    depth++;
                    return true;
                }

                @Override
                public boolean visitLeave(DependencyNode dn) {
                    depth--;
                    return true;
                }

            };
            node.accept(test);
            var nlg = new PreorderNodeListGenerator();
            node.accept(nlg);
            return nlg.getArtifacts(false).stream().map(a -> a.getFile().toPath()).toList();
        } catch (DependencyCollectionException | DependencyResolutionException ex) {
            Logger.getLogger(Dependencies.class.getName()).log(Level.SEVERE, null, ex);
            return Collections.emptyList();
        }
    }
}