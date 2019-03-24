---
layout: default
title: Bnd Indexed Maven Repository
description: Describes how to use the bnd Indexed Maven Repository plugin 
date: 2019-03-24 12:00:00
---

# Indexed Maven Repository Plugin

The Indexed Maven Repository Plugin maintains an index of a subset 
of one or more maven repositories and provides the information
as an OSGi repository for building and resolving.

The subset to index is configured using a directory structure. The
root directory is set with the plugin's `location` property (see below).
For each maven group (id) that is to be indexed, a sub directory
must be created. An initial directory structure should e.g. look like 
this:

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
from their `MANIFEST.MF`.

[^queryArtifacts]: If you are familiar with the maven repository
    layout, you know that there isn't really a way to retrieve
    all artifact ids for a given group id. However, all
    repositories that I know of provide a HTML index page
    for the directory level that represents a group. The
    artifact ids are extracted from this page.

The information obtained is stored in an 
[OSGi repository](https://osgi.org/javadoc/osgi.cmpn/7.0.0/org/osgi/service/repository/Repository.html). It is also persisted in a file `index.xml` in the directory
structure, using OSGi's
[XML Repository Format](https://osgi.org/specification/osgi.cmpn/7.0.0/service.repository.html#i3247820). Therefore, after the plugin has run, the 
directory structure might e.g. look like this:

<pre style="line-height: 1.1;">
indexed-maven/
├── de.mnl.osgi/
│   ├── group.properties
│   └── index.html
├── dependencies/
│   ├── org.jdrupes.httpcodec/
│   |   ├── group.properties
│   |   └── index.html
│   ├── org.jdrupes.httpcodec/
│   |   ├── group.properties
│   |   └── index.html
│   └── .../
├── org.jgrapes/
│   ├── group.properties
│   └── index.html
└── index.xml
</pre>

When the plugin is started for the next time (during Eclipse
startup or as part of a gradle run), the `index.xml` files 
are read back and used as initial content of the internal
resource repositories. Unless new artifacts or versions
are detected in the maven repositories, there is no need
to download the artifacts for re-building the 
index[^downloadArtifacts].

[^downloadArtifacts]: Of course, downloaded artifacts are cached
    locally, usually in the directory `~/.m2/repository/`, so 
    downloading again wouldn't be required. The advantage
    becomes apparent when using CI. Because the information
    can be restored from the `index.xml` files, there is no
    need to download any artifacts unless they are eventually
    required for building.


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

If the `location` property is not specified, the location is derived from
the repository name converted to lower case with spaces replaced with dashes,
and slashes replaced with colons.

---

