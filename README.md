# de.mnl.osgi

[![Build Status](https://travis-ci.org/mnlipp/de.mnl.osgi.svg?branch=master)](https://travis-ci.org/mnlipp/de.mnl.osgi)

A collection of miscellaneous OSGi bundles and components.

# Core Utils

| Bundle                   | Maven |
| ------------------------ | ------- |
| [de.mnl.osgi.coreutils](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.coreutils/javadoc/de/mnl/osgi/coreutils/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.coreutils.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.coreutils%22) |

A small bundle with utilities/helpers. Currently, it provides a new kind
of service tracker that works around the problem outlined 
[here](https://mnlipp.github.io/osgi-getting-started/TrackingAService.html).

# Logging Bridges/Facades

| Bundle                   | Maven |
| ------------------------ | ------- |
| [de.mnl.osgi.lf4osgi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.lf4osgi/javadoc/de/mnl/osgi/lf4osgi/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.lf4osgi.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.lf4osgi%22) | 
| [de.mnl.osgi.slf4j2osgi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.slf4j2osgi/javadoc/org/slf4j/impl/package-summary.html) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.slf4j2osgi.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.slf4j2osgi%22) | 
| [de.mnl.osgi.log4j2osgi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.log4j2osgi/javadoc/de/mnl/osgi/log4j2osgi/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.log4j2osgi.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.log4j2osgi%22) | 
| [de.mnl.osgi.jul2osgi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description)     | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.jul2osgi.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.jul2osgi%22) |
| [de.mnl.osgi.jul2osgi.lib](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.jul2osgi.lib.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.jul2osgi.lib%22) | 
| [de.mnl.osgi.osgi2jul](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.osgi2jul/javadoc/de/mnl/osgi/osgi2jul/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.osgi2jul.svg)](https://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.osgi2jul%22) | 

Logging in OSGi seems to be a notorious problem. Libraries—even if 
they have OSGi bundle headers—avoid dependencies on OSGi and therefore
use one of the "standard" logging libraries such as 
[java.util.logging](https://docs.oracle.com/javase/8/docs/technotes/guides/logging/overview.html),
[log4j](https://logging.apache.org/log4j/2.x/), [SLF4J](https://www.slf4j.org/)
or [commons logging](https://commons.apache.org/proper/commons-logging/).

When writing dedicated OSGi components, you should use the 
[Log Service](https://osgi.org/specification/osgi.cmpn/7.0.0/service.log.html),
but even with declarative services its usage is a bit cumbersome compared
to the ease of use that you get with the logging libraries. 

I've searched, but all attempts to solve the problem seemed rather complex to 
me. So I have written my own set of really simple to use bridging/facade bundles.
Contrary to some other "unification attempts", these bridges/facades put
OSGi logging in the center. All log events from the "standard" libraries
mentioned above are forwarded to the OSGi logging service, and all logging 
results can be obtained from the OSGi logging service (e.g. displayed
in the Apache Felix Web Console).

 * [LF for OSGi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.lf4osgi/javadoc/de/mnl/osgi/lf4osgi/package-summary.html#package.description):
   A logging facade for OSGi logging. Makes using OSGi logging as simple as
   using one of the "standard" libraries.
   
 * [SLF4J for OSGi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.slf4j2osgi/javadoc/org/slf4j/impl/package-summary.html#package.description):
   A SLF4J logging facade for OSGi logging. Built on LF4OSGi.
   
 * [Log4j to OSGi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.log4j2osgi/javadoc/de/mnl/osgi/log4j2osgi/package-summary.html#package.description):
   Not sure if this is a facade or a bridge. Anyway, forwards calls to the log4j 2 API
   to OSGi logging. Built on LF4OSGi.
   
 * [JUL to OSGi](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description):
   forwards all logging events from java.util.logging to the OSGi log service.
   
 * [OSGi to JUL](https://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.osgi2jul/javadoc/):
   invokes JUL compliant handlers for the messages logged with the OSGi log 
   service.


# Other Sub-Projects

See the READMEs in the subdirectories for details. 

# Usage

The easiest way to include the bundles in your bnd workspace build is to
add a `BndPomRepository`:

```
-plugin.xx.MnlOSGi: \
    aQute.bnd.repository.maven.pom.provider.BndPomRepository; \
        name=MnlOSGi; \
        readOnly=true; \
        releaseUrls=https://repo.maven.apache.org/maven2/; \
        query='q=g:%22de.mnl.osgi%22'
```