---
layout: default
title: Bnd Indexed Maven Repository
description: Describes how to use the bnd Indexed Maven Repository plugin 
date: 2019-04-20 12:00:00
---

# Indexed Maven Repository Plugin

The Indexed Maven Repository Plugin maintains an index of a subset 
of one or more maven repositories and provides the information
as an OSGi repository for building and resolving.

## Selecting GroupIds to index 

The subset to index is configured using a directory structure. The
root directory's location is set with the plugin's "`location`" property 
(see below). For each maven group (id) that is to be indexed, a sub 
directory must be created. An initial directory structure could e.g. 
look like this:

<pre style="line-height: 1.1;">
indexed-maven/
├── de.mnl.osgi/
└── org.jgrapes/
</pre>

When the plugin starts (or a refresh is triggered), it uses each
subdirectory name as group id and queries the configured maven
repositories for artifacts with this group id[^queryArtifacts]. 
The artifacts are retrieved from the maven repository and the 
capabilities and requirements of each artifact are extracted 
from their manifests.

[^queryArtifacts]: If you are familiar with the maven repository
    layout, you know that there isn't really a way to retrieve
    all artifact ids for a given group id. However, all
    repositories that I know of provide a HTML index page
    for the directory level that represents a group. The
    artifact ids are extracted from this page.

The information obtained is stored in an 
[OSGi repository](https://osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/repository/Repository.html). It is also persisted in a file "`index.xml`" in the directory
structure, using OSGi's
[XML Repository Format](https://osgi.org/specification/osgi.cmpn/7.0.0/service.repository.html#i3247820). Therefore, after the plugin has run, the 
directory structure changes to:

<pre style="line-height: 1.1;">
indexed-maven/
├── de.mnl.osgi/
│   └── index.html
├── dependencies/
│   ├── org.jdrupes.httpcodec/
│   │   └── index.html
│   ├── org.jdrupes.httpcodec/
│   │   └── index.html
│   └── .../
├── org.jgrapes/
│   └── index.html
└── index.xml
</pre>

When the plugin is started for the next time (during Eclipse
startup or as part of a gradle run), the "`index.xml`" files 
are read back and used as initial content of the internal
resource repositories. Unless new artifacts or versions
are detected in the maven repositories, there is thus no need
to download the artifacts for re-building the 
index[^downloadArtifacts].

[^downloadArtifacts]: Of course, downloaded artifacts are cached
    locally, usually in the directory "`~/.m2/repository/`", so 
    downloading again should not be required. The advantage
    becomes apparent when using CI. Because the information
    can be restored from the "`index.xml`" files, there is no
    need to download any artifacts unless they are eventually
    required for building.

Aside from the index files in the initially created directories,
the plugin has created an additional directory "`dependencies`" and
within this directory more directories with maven group ids
as name. Each of these automatically created directories
represents an additional resource repository that has been created
to hold one or more transitive (maven) dependencies of the 
artifacts in the explicitly selected maven groups.

Finally, there is a top-level "`index.xml`". It includes all
"`index.xml`" files from the sub-directories and may be used
to make the content of the indexed maven subset available
outside the project.

## Filtering ArtifactIds and Versions

Indexing a maven group id such as "org.apache.felix" results
in about 1200 artifacts. While this isn't a problem considering
today's disk space and connection speed, it may pose a problem
when you try to resolve requirements automatically. The plugin
therefore provides some filter settings. The settings are 
configured for each group id individually using a file 
"`group.properties`" in the group's directory.

### Select artifacts for indexing

To select which versions of an artifact are included in a group,
define a property "`[artifactId[.*];]versions=<maven version range>`". 
Here are some examples:

```properties
# Change default from "include everything" to "include nothing" 
versions=[,0)

# Include all versions of a specific artifact (only required if
# the default is to not include)
de.mnl.osgi.coreutils;versions=[0,)

# Include only version 1.0.0 and up for a given artifact
de.mnl.osgi.lf4osgi;versions=[1.0.0,)

# Include only versions 2.0.0 for a set of artifacts. Note that
# an artifact without ".*" is also affected by this filter
org.jgrapes.portal.*;version=[2.0.0,)
```

When you look at the content of the repositories after
an update, it is possible that you find versions which
weren't supposed to be included. This is because the
"versions" property filters only the list of "initial"
artifacts obtained from the repository. If a version
outside the "versions" range is referenced as a 
dependency in another artifact, it is added independent
of any "versions" specification.

### Excluding artifacts

To definitely exclude some versions of an artifact, use
the "`exclude`" property.

```properties
# There will be no version < 2.0.0 in the repository!
de.mnl.osgi.lf4osgi;exclude=[0,2.0.0)
```

Excluding is a powerful feature, because it does not only
affect an artifact, but also all other artifacts that
depend on it directly or indirectly. Excluding some 
"core artifact" of an old
version of something like Felix Apache can thus exclude
a complete old version of this OSGi framework implementation.
Excluding may prune a complete branch from the
dependency tree.

### Forcing inclusion of artifacts

In some cases, there may be a good old library
(works perfectly, has never been updated) that depends
on an old version of some base library. The old version
of the base library has been excluded, but you know that
the good old library works with a newer version of the
base library that is available. In such cases, it
is possible to force the inclusion of the good old
library despite the exclusion of its dependency by using
the "`forcedVersions`" property:

```properties
old.library;forcedVersions=[1.2.3,)
```

Contrary to the "`versions`" property, the "`exclude`" and 
"`forcedVersions`" properties can also be specified for 
groups in the "`dependencies`" directory[^example].

[^example]: A "real world" example of a configuration
    can be found [here](https://github.com/mnlipp/jgrapes-osgi/tree/master/cnf).

### Requesting special artifacts

By default, indexing includes the artifact produced by a maven project
if the extension specified in the POM equals "jar", "bundle" or
"eclipse-plugin". Starting with version 4.0.0 of this plugin, a "versions" 
property can also be used to request the inclusion of additional artifacts 
from a maven repository.

A use case for this is code coverage with 
[JaCoCo](https://www.eclemma.org/jacoco/). Doing this requires a javaagent 
that is available in the maven repository as an additional artifact with
classifier "runtime". By default, this artifact is not indexed, only the 
file that contains the "standard" artifact ("`org.jacoco.agent-x.y.z.jar`") 
is downloaded and indexed.

In order to retrieve and index the javaagent, you have to specify a filter
that also acts as a mapper by adding the file extension and classifier
of the requested file to the artifact id, using colons as separator:

```properties
org.jacoco.agent\:jar\:runtime;versions = [0.8.7,)
```

Note that you have to escape the colons used as separator, because they
are used in the key value of a property specification. This is definitely
a possible pitfall, but using a different separator that doesn't match
maven practices is just as bad.

Once the artifact is indexed, it can be referred to in bnd's `repo` macro
as shown in the bnd manual's 
["launching" chapter](https://bnd.bndtools.org/chapters/300-launching.html):

```properties
-runvm.coverage: \
	"-javaagent:${repo;org.jacoco:org.jacoco.agent:jar:runtime;latest}=destfile=${basedir}/generated/jacoco.exec,append=false"
```

## Plugin Configuration

```properties
-plugin.1.IndexedMaven: \
    de.mnl.osgi.bnd.repository.maven.provider.IndexedMavenRepositoryProvider; \
        path:="${workspace}/cnf/plugins/de.mnl.osgi.bnd.repository-1.0.0.jar,\
            ${workspace}/cnf/plugins/biz.aQute.repository-4.2.0.jar"; \
        name=IndexedMaven; \
        location=cnf/indexed-maven; \
        releaseUrls="https://repo.maven.apache.org/maven2/"
```

The plugin code is added with the `path` property. Besides the
actual plugin code, the bnd library for repository access must
be added (it is, of course, already contained in bnd, but it
is [not on bnd's classpath](https://github.com/bndtools/bnd/issues/2242)).

| Property   | Type   | Default  | Description                                |
| ---------- |:------:|:--------:| ------------------------------------------ |
| `local`     | PATH   | ~/.m2/repository | The file path to the local Maven repository |
| `name`      | NAME   |	IndexedMaven | The name of the repository          |
| `location`   | PATH   | (from name) | The file path where the index is maintained (see below) 
| `releaseUrls` | URL   | | Comma separated list of URLs to the repositories of released artifacts
| `snapshotUrls` | URL   | | Comma separated list of URLs to the repositories of snapshot artifacts
| `logIndexing` | `true|false` | `false` | If set to `true` a log is created in each directory that shows why artifact versions were added or left out

If the `location` property is not specified, the location is derived from
the repository name converted to lower case with spaces replaced with dashes,
and slashes replaced with colons.

---

