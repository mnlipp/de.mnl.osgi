/*
 * Extra Bnd Repository Plugins
 * Copyright (C) 2021  Michael N. Lipp
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

package de.mnl.osgi.bnd.repository.maven.idxmvn;

import aQute.maven.api.Archive;
import aQute.maven.api.Revision;
import de.mnl.osgi.bnd.maven.BoundArchive;
import de.mnl.osgi.bnd.maven.BoundRevision;
import de.mnl.osgi.bnd.maven.MavenVersion;
import de.mnl.osgi.bnd.maven.MavenVersionRange;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VersionSpecification {

    private Type type;
    private String artifactSpec;
    private String extension;
    private String classifier;
    private MavenVersionRange range;

    public static enum Type {
        VERSIONS("versions"), FORCED_VERSIONS("forcedVersions"),
        EXCLUDE("exclude");

        private static final Map<String, Type> types = new HashMap<>();
        static {
            Stream.of(values()).forEach(v -> types.put(v.keyword, v));
        }

        public final String keyword;

        private Type(String keyword) {
            this.keyword = keyword;
        }

        public static boolean isKeyword(String value) {
            return types.containsKey(value);
        }

        public static Type of(String value) {
            return Optional.ofNullable(types.get(value)).orElseThrow();
        }
    }

    public static VersionSpecification[] parse(Properties props) {
        List<VersionSpecification> result = new ArrayList<>();
        for (var key : props.keySet()) {
            String entry = (String) key;
            String[] entryParts = entry.split(";");
            if (!Type.isKeyword(entryParts[entryParts.length - 1])) {
                continue;
            }
            var spec = new VersionSpecification();
            spec.type = Type.of(entryParts[entryParts.length - 1]);
            String[] nameParts = entryParts.length == 1 ? new String[0]
                : entryParts[0].split(":");
            switch (nameParts.length) {
            case 3:
                spec.classifier = nameParts[2];
                // fallthrough
            case 2:
                spec.extension = nameParts[1];
                // fallthrough
            case 1:
                spec.artifactSpec = nameParts[0];
            }
            spec.range
                = MavenVersionRange.parseRange(props.getProperty(entry));
            result.add(spec);
        }
        return result.toArray(new VersionSpecification[result.size()]);

    }

    /**
     * Gets the type.
     *
     * @return the type
     */
    public Type getType() {
        return type;
    }

    /**
     * Match a revision against the version specifications and return
     * the archives that are matches by any specification.
     *
     * @param specs the specs
     * @param revision the revision
     * @return the sets the result
     */
    public static Set<Archive> toSelected(VersionSpecification[] specs,
            Revision revision) {
        if (specs.length == 0) {
            return Set.of(new Archive(revision, null, null, null));
        }
        return Arrays.stream(specs).filter(s -> Set
            .of(VersionSpecification.Type.VERSIONS,
                VersionSpecification.Type.FORCED_VERSIONS)
            .contains(s.getType()) && s.matches(revision.artifact)
            && s.range.includes(MavenVersion.from(revision.version)))
            .map(s -> {
                return new Archive(revision, null, s.extension, s.classifier);
            }).collect(Collectors.toSet());
    }

    /**
     * Match a revision against the version specifications and return
     * the archives that are matches by any specification.
     *
     * @param specs the specs
     * @param revision the revision
     * @return the sets the result
     */
    public static Set<BoundArchive> toSelected(VersionSpecification[] specs,
            BoundRevision revision) {
        return toSelected(specs, revision.unbound()).stream()
            .map(archive -> new BoundArchive(revision.mavenBackingRepository(),
                archive))
            .collect(Collectors.toSet());
    }

    public static boolean isForced(VersionSpecification[] specs,
            Archive archive) {
        return Arrays.stream(specs)
            .filter(s -> s.getType() == Type.FORCED_VERSIONS
                && s.matches(archive.revision.artifact)
                && s.range
                    .includes(MavenVersion.from(archive.revision.version)))
            .findAny().isPresent();
    }

    /**
     * Match a revision against the version specifications and return
     * the archives that are matches by any specification.
     *
     * @param specs the specs
     * @param artifact the artifact name
     * @return the sets the result
     */
    public static MavenVersionRange excluded(VersionSpecification[] specs,
            String artifact) {
        return Arrays.stream(specs).filter(s -> s.getType() == Type.EXCLUDE
            && s.matches(artifact))
            .map(s -> s.range).findFirst().orElse(MavenVersionRange.NONE);
    }

    /**
     * Match the given artifact name against the specification.
     *
     * @param name the name
     * @return true, if it matches
     */
    public boolean matches(String name) {
        // Try any and name unmodified
        if (artifactSpec == null || artifactSpec.equals(name)) {
            return true;
        }
        // Try <name>.*, successively removing trailing parts.
        String rest = name;
        while (true) {
            if (artifactSpec.equals(rest + ".*")) {
                return true;
            }
            int lastDot = rest.lastIndexOf('.');
            if (lastDot < 0) {
                break;
            }
            rest = rest.substring(0, lastDot);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder();
        if (artifactSpec != null) {
            result.append(artifactSpec);
        }
        if (extension != null) {
            result.append(":" + extension);
        }
        if (classifier != null) {
            result.append(":" + classifier);
        }
        if (result.length() > 0) {
            result.append(";");
        }
        result.append(type);
        result.append("=");
        result.append(range);
        return result.toString();
    }
}
