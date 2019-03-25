# de.mnl.osgi.bnd.repository

## Indexed Maven Repository

The Indexed Maven Repository Plugin maintains an index of a subset 
of one or more maven repositories and provides the information
as an OSGi repository for building and resolving.

The plugin has a [documentation page of its own](https://mnlipp.github.io/de.mnl.osgi/IndexedMavenRepository.html).

## NexusSearchRepositoryProvider

A plugin for the [bnd](http://bnd.bndtools.org/) tool that provides
the results of a Nexus search as an OSGi repository. It uses the
REST services as defined 
[here](https://oss.sonatype.org/nexus-restlet1x-plugin/default/docs/index.html)
and
[here](https://repository.sonatype.org/nexus-indexer-lucene-plugin/default/docs/rest.html).

The bnd tool comes with a plugin that provides the results from 
[searching Maven Central](http://bnd.bndtools.org/plugins/pomrepo.html). This,
however, does not allow you to easily access uploaded snapshots of
bundles under development. (Details about *why* you need such plugins
in the first place can be found 
[here](http://mnlipp.github.io/osgi-getting-started/Repositories.html)).

Here's a sample configuration of the plugin:

```properties
-plugin.8.JGrapes: \
    de.mnl.osgi.bnd.repository.maven.provider.NexusSearchRepositoryProvider; \
        path:="${workspace}/cnf/plugins/de.mnl.osgi.bnd.repository-x.x.x.jar,\
            ${workspace}/cnf/plugins/biz.aQute.repository-4.0.0.jar"; \
        name=JGrapes; \
        server="https://oss.sonatype.org"; \
        query="g=org.jgrapes"
```

The definition follows the pattern described in the 
[bnd manual](http://bnd.bndtools.org/chapters/610-plugin.html). Note
that the paths in the `path:` directive are relative to the bnd workspace,
so the `${workspace}/` can be omitted. The `path:` includes the plugin
itself and a library from bnd that is not in the default classpath
used by bnd for plugins. The latest version of this plugin can be
obtained from the 
[release directory](https://github.com/mnlipp/de.mnl.osgi/tree/master/cnf/release/de.mnl.osgi.bnd.repository)
on github. The bnd library can be downloaded from Maven Central or
copied from the eclipse plugins directory.

The `name` property defaults to `OssSonatype`, the `server` property 
to `https://oss.sonatype.org`.

The `query` property may be a comma separated list of queries that are executed
in parallel.

The optional `searchBreadth` property (default value: 3) restricts the
artifacts found to the given number of (latest) versions.

The optional `chunkSize` property (default value: 500) splits the queries
in parallel queries with ranges of the given size. Note that results
are lost if the chunk size is too big.