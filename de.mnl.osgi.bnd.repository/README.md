# de.mnl.osgi.bnd.repository

## NexusSearchRepositoryProvider

A plugin for the [bnd](http://bnd.bndtools.org/) tool that provides
the results of a Nexus search as an OSGi repository.

The bnd tool comes with a plugin that provides the results from 
[searching Maven Central](http://bnd.bndtools.org/plugins/pomrepo.html). This,
however, does not allow you to easily access uploaded snapshots of
bundles under development. (Details about *why* you need such plugins
in the first place can be found 
[here](http://mnlipp.github.io/osgi-getting-started/Repositories.html).

Here's a sample configuration of the plugin:

```properties
-plugin.8.JGrapes: \
    de.mnl.osgi.bnd.repository.maven.provider.NexusSearchRepositoryProvider; \
        path:="${workspace}/cnf/plugins/de.mnl.osgi.bnd.repository-x.x.x.jar,\
            ${workspace}/cnf/plugins/biz.aQute.repository-3.5.0.jar"; \
        name=JGrapes; \
        server="https://oss.sonatype.org"; \
        query="g=org.jgrapes"
```

The definition follows the pattern described in the 
[bnd manual](http://bnd.bndtools.org/chapters/610-plugin.html). Note
that the paths in the `path:` directive are relative to the bnd workspace,
so the `${workspace}/` can be omitted. The `path:` includes the plugin
itself and a library from bnd that is not in the default classpath
that bnd creates for plugins.

The `name` property defaults to `OssSonatype`, the `server` property 
to `https://oss.sonatype.org`.