# de.mnl.osgi

[![Build Status](https://travis-ci.org/mnlipp/de.mnl.osgi.svg?branch=master)](https://travis-ci.org/mnlipp/de.mnl.osgi)

A collection of miscellaneous OSGi bundles and components.

# Logging Bridges

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

 * [JUL to OSGi](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.jul2osgi/javadoc/):
   forwards all information to the OSGi log service.
   
 * [OSGi to JUL](http://mnlipp.github.io/de.mnl.osgi/de.mnl.osgi.osgi2jul/javadoc/):
   invokes JUL compliant handlers for the messages logged with the OSGi log 
   service.


# Other Sub-Projects

See the READMEs in the subdirectories for details. 