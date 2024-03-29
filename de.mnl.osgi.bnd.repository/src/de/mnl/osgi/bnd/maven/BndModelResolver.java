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

import aQute.maven.api.Archive;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import org.apache.maven.building.FileSource;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;

/**
 * Resolves (raw) model requests using a bnd {@link MavenRepository}
 * as backend.  
 */
@SuppressWarnings("deprecation")
public class BndModelResolver implements ModelResolver, Cloneable {

    private final MavenRepository bndRepository;
    @SuppressWarnings({ "PMD.SingularField", "unused" })
    private final Reporter reporter;

    /**
     * Instantiates a new bnd model resolver.
     *
     * @param bndRepository the backing repository
     * @param reporter the reporter
     */
    public BndModelResolver(MavenRepository bndRepository, Reporter reporter) {
        this.bndRepository = bndRepository;
        this.reporter = reporter;
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.PreserveStackTrace" })
    public ModelSource resolveModel(String groupId, String artifactId,
            String version) throws UnresolvableModelException {
        Revision revision
            = Program.valueOf(groupId, artifactId).version(version);
        Archive pomArchive = revision.getPomArchive();
        try {
            File pomFile = bndRepository.get(pomArchive).getValue();
            if (pomFile == null) {
                throw new UnresolvableModelException("Not found.", groupId,
                    artifactId, version);
            }
            return new FileModelSource(pomFile);
        } catch (InvocationTargetException e) {
            throw new UnresolvableModelException(e.getCause(), groupId,
                artifactId, version);
        } catch (Exception e) {
            throw new UnresolvableModelException(e, groupId, artifactId,
                version);
        }
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.PreserveStackTrace" })
    public ModelSource resolveModel(Parent parent)
            throws UnresolvableModelException {
        Revision revision
            = Program.valueOf(parent.getGroupId(), parent.getArtifactId())
                .version(parent.getVersion());
        try {
            Archive pomArchive
                = bndRepository.getResolvedArchive(revision, "pom", "");
            parent.setVersion(pomArchive.getRevision().version.toString());
            File pomFile = bndRepository.get(pomArchive).getValue();
            if (pomFile == null) {
                throw new UnresolvableModelException("Not found.",
                    parent.getGroupId(), parent.getArtifactId(),
                    parent.getVersion());
            }
            return new FileModelSource(pomFile);
        } catch (InvocationTargetException e) {
            throw new UnresolvableModelException(e.getCause(),
                parent.getGroupId(), parent.getArtifactId(),
                parent.getVersion());
        } catch (Exception e) {
            throw new UnresolvableModelException(e, parent.getGroupId(),
                parent.getArtifactId(), parent.getVersion());
        }
    }

    @Override
    @SuppressWarnings({ "PMD.AvoidCatchingGenericException",
        "PMD.PreserveStackTrace" })
    public ModelSource resolveModel(Dependency dependency)
            throws UnresolvableModelException {
        Revision revision
            = Program.valueOf(dependency.getGroupId(),
                dependency.getArtifactId()).version(dependency.getVersion());
        try {
            Archive pomArchive
                = bndRepository.getResolvedArchive(revision, "pom", "");
            dependency.setVersion(pomArchive.getRevision().version.toString());
            File pomFile = bndRepository.get(pomArchive).getValue();
            if (pomFile == null) {
                throw new UnresolvableModelException("Not found.",
                    dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getVersion());
            }
            return new FileModelSource(pomFile);
        } catch (InvocationTargetException e) {
            throw new UnresolvableModelException(e.getCause(),
                dependency.getGroupId(), dependency.getArtifactId(),
                dependency.getVersion());
        } catch (Exception e) {
            throw new UnresolvableModelException(e, dependency.getGroupId(),
                dependency.getArtifactId(), dependency.getVersion());
        }
    }

    @Override
    public void addRepository(Repository repository)
            throws InvalidRepositoryException {
        // Ignored, repository is defined by constructor.
    }

    @Override
    public void addRepository(Repository repository, boolean replace)
            throws InvalidRepositoryException {
        // Ignored, repository is defined by constructor.
    }

    @Override
    public ModelResolver newCopy() {
        try {
            return (BndModelResolver) clone();
        } catch (CloneNotSupportedException e) {
            // Doesn't happen
            return null;
        }
    }

    /**
     * The Class FileModelSource.
     */
    public static class FileModelSource extends FileSource
            implements ModelSource {

        /**
         * Instantiates a new file model source.
         *
         * @param file the file
         */
        public FileModelSource(File file) {
            super(file);
        }
    }
}
