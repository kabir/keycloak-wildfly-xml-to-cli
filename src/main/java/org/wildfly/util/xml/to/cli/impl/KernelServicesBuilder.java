/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.wildfly.util.xml.to.cli.impl;

import javax.xml.stream.XMLStreamException;

/**
 * A builder to create a controller and initialize it with the passed in subsystem xml or boot operations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
interface KernelServicesBuilder {
    /**
     * Sets the subsystem xml to be parsed to create the boot operations used to initialize the controller
     * @param subsystemXml the subsystem xml
     * @return this builder
     * @throws XMLStreamException if there were problems parsing the xml
     */
    KernelServicesBuilder setSubsystemXml(String subsystemXml) throws XMLStreamException;

    /**
     * Creates the controller and initializes it with the passed in configuration options.
     * @throws IllegalStateException if #build() has already been called
     * @return the kernel services wrapping the controller
     */
    KernelServices build() throws Exception;
}
