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

import aQute.maven.api.IMavenRepo;
import aQute.maven.api.Program;
import aQute.maven.api.Revision;
import aQute.maven.provider.MavenBackingRepository;
import aQute.maven.provider.MavenRepository;
import aQute.service.reporter.Reporter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * Provides a common {@link IMavenRepo} view on several 
 * {@link MavenBackingRepository} instances.
 * <P>
 * The class extends {@link MavenRepository} which lacks some
 * required functionality.
 */
public class MergingMavenRepository extends MavenRepository
        implements IMavenRepo, Closeable {

    /**
     * Instantiates a new merged maven repository.
     *
     * @param base the base
     * @param repoId the repository id
     * @param releaseRepos the backing release repositories
     * @param snapshotRepos the backing snapshot repositories
     * @param executor an executor
     * @throws Exception the exception
     */
    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidDuplicateLiterals" })
    public MergingMavenRepository(File base, String repoId,
            List<MavenBackingRepository> releaseRepos,
            List<MavenBackingRepository> snapshotRepos, Executor executor,
            Reporter reporter)
            throws Exception {
        super(base, repoId, releaseRepos, snapshotRepos, executor, reporter);
    }

    /**
     * Returns all repositories.
     *
     * @return the list
     */
    public List<MavenBackingRepository> allRepositories() {
        List<MavenBackingRepository> result
            = new ArrayList<>(getReleaseRepositories());
        result.addAll(getSnapshotRepositories());
        return result;
    }

    /**
     * Returns all known revisions as {@link BoundRevision}s.
     *
     * @param program the program
     * @return the bound revisions
     * @throws Exception the exception
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public List<BoundRevision> boundRevisions(Program program)
            throws Exception {
        List<BoundRevision> revisions = new ArrayList<>();

        for (MavenBackingRepository mbr : getReleaseRepositories()) {
            addRevisions(mbr, program, revisions);
        }
        for (MavenBackingRepository mbr : getSnapshotRepositories()) {
            if (!getReleaseRepositories().contains(mbr)) {
                addRevisions(mbr, program, revisions);
            }
        }
        return revisions;
    }

    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidInstantiatingObjectsInLoops" })
    private void addRevisions(MavenBackingRepository mbr, Program program,
            List<BoundRevision> revisions) throws Exception {
        List<Revision> revs = new ArrayList<>();
        mbr.getRevisions(program, revs);
        for (Revision rev : revs) {
            revisions.add(new BoundRevision(mbr, rev));
        }
    }

    /**
     * Converts a {@link Revision} to an {@link BoundRevision}.
     *
     * @param revision the revision
     * @return the bound revision
     * @throws IOException 
     */
    @SuppressWarnings("PMD.SignatureDeclareThrowsException")
    public Optional<BoundRevision> toBoundRevision(Revision revision)
            throws IOException {
        for (MavenBackingRepository mbr : getReleaseRepositories()) {
            Optional<BoundRevision> checkResult
                = toBoundRevision(mbr, revision);
            if (checkResult.isPresent()) {
                return checkResult;
            }
        }
        for (MavenBackingRepository mbr : getSnapshotRepositories()) {
            if (!getReleaseRepositories().contains(mbr)) {
                Optional<BoundRevision> checkResult
                    = toBoundRevision(mbr, revision);
                if (checkResult.isPresent()) {
                    return checkResult;
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings({ "PMD.SignatureDeclareThrowsException",
        "PMD.AvoidRethrowingException", "PMD.AvoidCatchingGenericException",
        "PMD.AvoidThrowingRawExceptionTypes" })
    private Optional<BoundRevision> toBoundRevision(MavenBackingRepository mbr,
            Revision revision) throws IOException {
        List<Revision> revs = new ArrayList<>();
        try {
            mbr.getRevisions(revision.program, revs);
        } catch (IOException e) {
            // Should be the only reason.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (revs.contains(revision)) {
            return Optional.of(new BoundRevision(mbr, revision));
        }
        return Optional.empty();
    }

}
