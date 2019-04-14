/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2019  Michael N. Lipp
 * 
 * This program is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Affero General Public License as published by 
 * the Free Software Foundation; either version 3 of the License, or 
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License along 
 * with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package de.mnl.osgi.bnd.maven;

import static aQute.bnd.osgi.repository.BridgeRepository.addInformationCapability;

import aQute.bnd.osgi.resource.CapabilityBuilder;
import aQute.bnd.osgi.resource.ResourceBuilder;
import aQute.maven.api.Archive;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.service.reporter.Reporter;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuildingException;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Resource;

/**
 * Wraps the artifacts from a maven reposityor as {@link Resource}s.
 */
public class MavenResourceRepository extends CompositeMavenRepository {

    /** The namespace used to store the maven dependencies information. */
    public static final String MAVEN_DEPENDENCIES_NS
        = "maven.dependencies.info";

    private Function<Revision, Optional<Resource>> resourceSupplier
        = resource -> Optional.empty();
    private final Map<Revision, MavenResource> resourceCache
        = new ConcurrentHashMap<>();

    public MavenResourceRepository(File base, String repoId,
            List<MavenBackingRepository> releaseRepos,
            List<MavenBackingRepository> snapshotRepos, Executor executor,
            Reporter reporter) throws Exception {
        super(base, repoId, releaseRepos, snapshotRepos, executor, reporter);
    }

    @Override
    public void reset() {
        super.reset();
        resourceCache.clear();
    }

    /**
     * Sets a function that can provide resource information more
     * efficiently (e.g. from some local persistent cache) than
     * the remote maven repository.
     * <P>
     * Any resource information provided by the function must be
     * complete, i.e. must hold the information from the "bnd.info"
     * namespace and from the "maven.dependencies.info" namespace.
     *
     * @param resourceSupplier the resource supplier
     * @return the composite maven repository
     */
    public MavenResourceRepository setResourceSupplier(
            Function<Revision, Optional<Resource>> resourceSupplier) {
        this.resourceSupplier = resourceSupplier;
        return this;
    }

    /**
     * Creates a {@link MavenResource} for the given revision. 
     *
     * @param revision the revision
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     */
    public MavenResource resource(BoundRevision revision,
            BinaryLocation location) {
        return resourceCache.computeIfAbsent(revision.unbound(),
            rev -> resourceSupplier.apply(rev)
                .map(resource -> new MavenResourceImpl(revision, resource))
                .orElseGet(() -> new MavenResourceImpl(revision, location)));
    }

    /**
     * Creates a {@link MavenResource} for the given program and version. 
     *
     * @param program the program
     * @param version the version
     * @param location which URL to use for the binary in the {@link Resource}
     * @return the resource
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public Optional<MavenResource> resource(Program program,
            MavenVersionSpecification version, BinaryLocation location)
            throws IOException {
        return find(program, version)
            .map(revision -> resource(revision, location));
    }

    /**
     * Retrieves the dependency information from the provided
     * resource. Assumes that the resource was created by this
     * repository, i.e. with capabilities in the
     * "maven.dependencies.info" name space.
     *
     * @param resource the resource
     * @param dependencies the dependencies
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    private static void retrieveDependencies(Resource resource,
            Collection<Dependency> dependencies) {
        // Actually, there should be only one such capability per resource.
        for (Capability capability : resource
            .getCapabilities(MAVEN_DEPENDENCIES_NS)) {
            Map<String, Object> depAttrs = capability.getAttributes();
            depAttrs.values().stream()
                .flatMap(val -> COORDS_SPLITTER.splitAsStream((String) val))
                .map(rev -> {
                    String[] parts = rev.split(":");
                    Dependency dep = new Dependency();
                    dep.setGroupId(parts[0]);
                    dep.setArtifactId(parts[1]);
                    dep.setVersion(parts[2]);
                    return dep;
                }).forEach(dependencies::add);
        }
    }

    /**
     * A maven resource that obtains its information
     * lazily from a {@link CompositeMavenRepository}.
     */
    public class MavenResourceImpl implements MavenResource {

        private final Revision revision;
        private BoundRevision cachedRevision;
        private Resource cachedDelegee;
        private List<Dependency> cachedDependencies;
        private final BinaryLocation location;

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(Revision revision, BinaryLocation location) {
            this.revision = revision;
            this.location = location;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(BoundRevision revision,
                BinaryLocation location) {
            this.revision = revision.unbound();
            this.cachedRevision = revision;
            this.location = location;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(Revision revision, Resource resource) {
            this.revision = revision;
            this.cachedDelegee = resource;
            // Doesn't matter, resource won't be created (already there).
            this.location = BinaryLocation.REMOTE;
        }

        /**
         * Instantiates a new maven resource from the given data.
         *
         * @param revision the revision
         * @param resource the delegee
         */
        private MavenResourceImpl(BoundRevision revision, Resource resource) {
            this.revision = revision.unbound();
            this.cachedRevision = revision;
            this.cachedDelegee = resource;
            // Doesn't matter, resource won't be created (already there).
            this.location = BinaryLocation.REMOTE;
        }

        @Override
        public Revision revision() {
            return revision;
        }

        @Override
        public BoundRevision boundRevision() throws MavenResourceException {
            try {
                if (cachedRevision == null) {
                    cachedRevision = find(revision).get();
                }
                return cachedRevision;
            } catch (IOException e) {
                throw new MavenResourceException(e);
            }
        }

        @Override
        public Resource asResource() throws MavenResourceException {
            if (cachedDelegee == null) {
                createResource();
            }
            return cachedDelegee;
        }

        /**
         * Creates a {@link Resource} representation from the manifest
         * of the artifact.  
         *
         * @throws IOException Signals that an I/O exception has occurred.
         * @throws ModelBuildingException the model building exception
         * @throws UnresolvableModelException the unresolvable model exception
         */
        @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
            "PMD.AvoidInstanceofChecksInCatchClause",
            "PMD.CyclomaticComplexity", "PMD.NcssCount" })
        private void createResource() throws MavenResourceException {
            Model model = model(revision);
            Archive archive = revision.archive(model.getPackaging(), "");
            ResourceBuilder builder = new ResourceBuilder();
            addInformationCapability(builder, archive.toString(),
                archive.getRevision().toString(), null);
            if (model.getPackaging().equals("jar")) {
                File binary;
                try {
                    binary = get(archive);
                } catch (IOException e) {
                    throw new MavenResourceException(e);
                }
                try {
                    if (location == BinaryLocation.LOCAL) {
                        builder.addFile(binary, binary.toURI());
                    } else {
                        builder.addFile(binary,
                            boundRevision().mavenBackingRepository()
                                .toURI(archive.remotePath));
                    }
                } catch (Exception e) {
                    // That's what the exceptions thrown here come down to.
                    throw new IllegalArgumentException(e);
                }
            }

            // Add dependency infos
            if (!dependencies().isEmpty()) {
                CapabilityBuilder cap
                    = new CapabilityBuilder(MAVEN_DEPENDENCIES_NS);
                Set<Dependency> cpDeps = new HashSet<>();
                Set<Dependency> rtDeps = new HashSet<>();
                for (Dependency dep : dependencies()) {
                    if (dep.getScope() == null
                        || dep.getScope().equalsIgnoreCase("compile")) {
                        cpDeps.add(dep);
                    } else if (dep.getScope().equalsIgnoreCase("runtime")) {
                        rtDeps.add(dep);
                    }
                }
                try {
                    if (!cpDeps.isEmpty()) {
                        cap.addAttribute("compile", toVersionList(cpDeps));
                    }
                    if (!rtDeps.isEmpty()) {
                        cap.addAttribute("runtime", toVersionList(rtDeps));
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
                builder.addCapability(cap);
            }
            cachedDelegee = builder.build();
        }

        private String toVersionList(Collection<Dependency> deps) {
            StringBuilder depsList = new StringBuilder("");
            for (Dependency dep : deps) {
                if (depsList.length() > 0) {
                    depsList.append(';');
                }
                depsList.append(dep.getGroupId());
                depsList.append(':');
                depsList.append(dep.getArtifactId());
                depsList.append(':');
                depsList.append(dep.getVersion());
            }
            return depsList.toString();
        }

        @Override
        public List<Capability> getCapabilities(String namespace)
                throws MavenResourceException {
            return asResource().getCapabilities(namespace);
        }

        @Override
        public List<Requirement> getRequirements(String namespace)
                throws MavenResourceException {
            return asResource().getRequirements(namespace);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }
            if (obj instanceof MavenResource) {
                return revision.equals(((MavenResource) obj).revision());
            }
            if (obj instanceof Resource) {
                try {
                    return asResource().equals(obj);
                } catch (MavenResourceException e) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            return revision.hashCode();
        }

        @Override
        public String toString() {
            return revision.toString();
        }

        @Override
        @SuppressWarnings("PMD.ConfusingTernary")
        public final List<Dependency> dependencies()
                throws MavenResourceException {
            if (cachedDependencies == null) {
                if (cachedDelegee != null) {
                    cachedDependencies = new ArrayList<>();
                    retrieveDependencies(cachedDelegee, cachedDependencies);
                } else {
                    cachedDependencies = MavenResourceRepository.this
                        .model(revision()).getDependencies().stream()
                        .filter(dep -> !dep.getGroupId().contains("$")
                            && !dep.getArtifactId().contains("$")
                            && !dep.isOptional()
                            && (dep.getScope() == null
                                || dep.getScope().equals("compile")
                                || dep.getScope().equals("runtime")
                                || dep.getScope().equals("provided")))
                        .collect(Collectors.toList());
                }
            }
            return cachedDependencies;
        }

    }
}
