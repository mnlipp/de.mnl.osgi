# de.mnl.osgi

[![Build Status](https://travis-ci.org/mnlipp/de.mnl.osgi.svg?branch=master)](https://travis-ci.org/mnlipp/de.mnl.osgi)

A collection of miscellaneous OSGi bundles and components.

# Core Utils

| Bundle                   | Maven |
| ------------------------ | ------- |
| [de.mnl.osgi.coreutils](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.coreutils/javadoc/de/mnl/osgi/coreutils/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.coreutils.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.coreutils%22) |

A small bundle with utilities/helpers. Currently, it provides a new kind
of service tracker that works around the problem outlined 
[here](https://mnlipp.github.io/osgi-getting-started/TrackingAService.html).

# Logging Bridges

| Bundle                   | Maven |
| ------------------------ | ------- |
| [de.mnl.osgi.jul2osgi](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description)     | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.jul2osgi.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.jul2osgi%22) |
| [de.mnl.osgi.jul2osgi.lib](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.jul2osgi.lib.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.jul2osgi.lib%22) | 
| [de.mnl.osgi.osgi2jul](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.osgi2jul/javadoc/de/mnl/osgi/osgi2jul/package-summary.html#package.description) | [![Maven Central](https://img.shields.io/maven-central/v/de.mnl.osgi/de.mnl.osgi.osgi2jul.svg)](http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22de.mnl.osgi.osgi2jul%22) | 

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
me. So I have written my own set of really simple to use bridging bundles.

 * [JUL to OSGi](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/de/mnl/osgi/jul2osgi/package-summary.html#package.description):
   forwards all logging events from java.util.logging to the OSGi log service.
   
 * [OSGi to JUL](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.osgi2jul/javadoc/):
   invokes JUL compliant handlers for the messages logged with the OSGi log 
   service.


# Other Sub-Projects

See the READMEs in the subdirectories for details. 
