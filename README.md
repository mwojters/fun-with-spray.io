ShowUp
======

Very simple REST server implementing requested services.

Configuration for groups and files can be set in conf file:

    src/main/resources/application.conf

Installation
------------
To install app, you need to invoke [Gradle 2.1](http://www.gradle.org/) command in project dir:

    gradle install

this will hopefully create _build_ folder with compiled bytecode and downloaded dependency libs.

Running
-------
Application requires:

1. [Java JRE](http://www.oracle.com/technetwork/java/javase/downloads/index.html) version 6 or later

2. Environment variable JAVA_HOME pointing to Java JRE install directory

Start application by invoking start script _showup_ from directory:    

    build/install/showup/bin

Default file encoding is set to UTF-8 and can be changed in start script.

Implementation
--------------
Application uses [Spray.io](http://spray.io/) implementation of HTTP based REST server.
It employs Akka actors to achieve asynchronous processing.

Remarks
-------
Implemented file cache uses line character offset for faster random line retrievals. It gives better performance than
reading all lines from beginning of the file, nonetheless it is still very slow for big files. Retrieval could be 
improved _dramatically_ by replacing character offset with binary offset. That way we could position file channel before
read and do not decode all characters only to skip them.

**Warning**: File caching requires ~16 bytes of memory per line. Therefore for huge files with short or empty
lines cache will consume much amounts of memory. For example cache for 1 GiB file with 100 chars per line will consume
almost 11 MiB.