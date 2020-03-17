jai-imageio-jpeg2000
====================

[![Build Status](https://travis-ci.org/jai-imageio/jai-imageio-jpeg2000.svg)](https://travis-ci.org/jai-imageio/jai-imageio-jpeg2000)

JPEG2000 support for Java Advanced Imaging Image I/O Tools API core
[jai-imagecore-core](https://github.com/jai-imageio/jai-imageio-core).

The `jj2000` package in this module is licensed under the
[JJ2000 license](LICENSE-JJ2000.txt) and is therefore
[not compatible with the GPL 3 license](https://github.com/jai-imageio/jai-imageio-core/issues/4).

**WARNING** - from the [JJ2000 license](LICENSE-JJ2000.txt):

> Those intending to use
> this software module in hardware or software products are advised that
> their use may infringe existing patents. The original developers of
> this software module, JJ2000 Partners and ISO/IEC assume no liability
> for use of this software module or modifications thereof. No license
> or right to this software module is granted for non JPEG 2000 Standard
> conforming products.

**NOTE**: This is a module extracted from the
[java.net project jai-imageio-core](https://java.net/projects/jai-imageio-core/).
It depends on the [jai-imageio-core](https://github.com/jai-imageio/jai-imageio-core)
module.

There is **NO FURTHER DEVELOPMENT** in this repository; any commits here are
just to keep the build working with recent versions of Maven/Java.

If you are not concerned about GPL compatibility or source code
availability, you might instead want to use
https://github.com/geosolutions-it/imageio-ext/ which is actively
maintained and extends the original imageio with many useful features,
but depends on the
[binary distribution of jai_core](http://download.osgeo.org/webdav/geotools/javax/media/jai_core/1.1.3/).

[![xkcd 2254](https://imgs.xkcd.com/comics/jpeg2000.png)](https://xkcd.com/2254/)

## Contribute

You are welcome to raise 
[Github pull requests](https://github.com/jai-imageio/jai-imageio-jpeg2000/pulls) for any improvements,
or to create [GitHub issues](https://github.com/jai-imageio/jai-imageio-jpeg2000/issues) for any bugs 
discovered.

This project is maintained fully on GitHub by its community - to follow the project, simply
[watch this project on GitHub](https://github.com/jai-imageio/jai-imageio-jpeg2000/subscription).


Usage
-----

To build this project, use Apache Maven 2.2.1 or newer and run:

    mvn clean install

To use jai-imageio-core-jpeg2000 from a Maven project, add:

    <dependency>
        <groupId>com.github.jai-imageio</groupId>
        <artifactId>jai-imageio-jpeg2000</artifactId>
        <version>1.3.0</version>
    </dependency>

To find the latest `<version>` above, see
[jai-imageio-jpeg2000 at BinTray](https://bintray.com/jai-imageio/maven/jai-imageio-jpeg2000)


jai-imageio-jpeg2000 is mirrored to Maven Central. Alternatively you can use
this explicit repository:

    <repositories>
      <repository>
        <id>bintray-jai-imageio</id>
        <name>jai-imageio at bintray</name>
        <url>http://dl.bintray.com/jai-imageio/maven/</url>
        <snapshots>
          <enabled>false</enabled>
        </snapshots>
      </repository>
    </repositories>

The Maven repository include additional artifact types such as `javadoc` and `sources`
which should be picked up by your IDE's Maven integration.


Download
--------

To download the binary JARs, browse the
[Downloads at BinTray](https://bintray.com/jai-imageio/maven/jai-imageio-jpeg2000).


Javadoc
-------

* [Javadoc for jai-imageio-jpeg2000](http://jai-imageio.github.io/jai-imageio-jpeg2000/javadoc/)
* [Javadoc for jai-imageio-core](http://jai-imageio.github.io/jai-imageio-core/javadoc/)




Copyright and licenses
----------------------

* Copyright © 1999/2000 JJ2000 Partners
* Copyright © 2005 Sun Microsystems
* Copyright © 2010-2015 University of Manchester
* Copyright © 2014-2015 Stian Soiland-Reyes
* Copyright © 2015 Yannick De Turck

The complete copyright notice for this project is in
[COPYRIGHT.md](COPYRIGHT.md)

The source code license for the
[com.github.jaiimageio.jpeg2000](src/main/java/com/github/jaiimageio/jpeg2000) package
and the build modifications (e.g. `pom.xml` and [tests](src/test))
are licensed under a **BSD 3-Clause license** with an additional
nuclear disclaimer, see [LICENSE-Sun.txt](LICENSE-Sun.txt)

The [jj2000](src/main/java/jj2000) package in this module is licensed under the
[JJ2000 license](LICENSE-JJ2000.txt) which is **not compatible
with the GNU Public License (GPL)**. It is unknown what is the compatibility
of the JJ2000 license with other open source licenses.


Changelog
---------

* 1.3.1 - Now an OSGi bundle
* 1.3.0 - Changed package name to org.github.imageio.plugins.jpeg2000.
      Added JPEG2000 test. Java 8-workaround in test.
* 1.2-pre-dr-b04-2014-09-13 - Include jpeg2000 plugin that was remaining in jai-imageio-core. 
      Improved javadoc.
* 1.2-pre-dr-b04-2014-09-12  Separated out [JPEG 2000](https://github.com/jai-imageio/jai-imageio-core/issues/4)
      support from [jai-imageio-core](http://github.com/jai-imageio/jai-imageio-core)
      for [licensing reasons](https://github.com/jai-imageio/jai-imageio-core/issues/4)


More info
---------

* https://github.com/jai-imageio/jai-imageio-jpeg2000
* https://github.com/jai-imageio/jai-imageio-core
* http://jai-imageio.github.io/jai-imageio-core/javadoc/
* https://java.net/projects/jai-imageio-core/
* http://www.oracle.com/technetwork/java/current-142188.html
* http://download.java.net/media/jai/builds/release/1_1_3/
