# Mobeelizer SDK for Java

The Mobeelizer SDK is a framework to connect Java application with [Mobeelizer](http://www.mobeelizer.com/) platform.

Both [Javadoc JAR](http://sdk.mobeelizer.com/mobeelizer-java-sdk-javadoc.jar) and [online documentation](http://sdk.mobeelizer.com/java/index.html) are available.

Please visit the [Mobeelizer site](http://www.mobeelizer.com/) to get more informations.

Ready-to-use version of the Mobeelizer SDK framework is deployed at [http://sdk.mobeelizer.com/mobeelizer-java-sdk.jar](http://sdk.mobeelizer.com/mobeelizer-java-sdk.jar).

# Dependencies

* commons-logging.commons-logging 1.1.1
* org.apache.httpcomponents.httpcore 4.1.3
* org.apache.httpcomponents.httpclient 4.1.3
* org.apache.httpcomponents.httpmime 4.1.3
* org.json.json 20090211
* org.slf4j.slf4j-api 1.6.4

# Maven

Mobeelizer SDK is also distibuted using Maven.

	<dependencies>
		<dependency>
			<groupId>com.mobeelizer</groupId>
			<artifactId>java-sdk</artifactId>
			<version>1.1.0</version>
		</dependency>
	</dependencies>
	
	<repositories>
		<repository>
			<id>qcadoo-releases-repository</id>
			<url>http://nexus.qcadoo.org/content/repositories/releases</url>
		</repository>
	</repositories>

# Copyright

Copyright 2012 Mobeelizer Ltd

Mobeelizer SDK is free software; you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
 
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
 
You should have received a copy of the GNU Affero General Public License along with this program; if not, write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301 USA