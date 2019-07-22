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

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;

/**
 * Internal class.
 * Used to create the boot operations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class BootOperationsBuilder {
    private final BootOperationParser xmlParser;
    private List<ModelNode> bootOperations = Collections.emptyList();
    private String subsystemXml;
    private boolean built;

    public BootOperationsBuilder(BootOperationParser xmlParser) {
        this.xmlParser = xmlParser;
    }

    public BootOperationsBuilder setXml(String subsystemXml) throws XMLStreamException {
        validateNotAlreadyBuilt();
        this.subsystemXml = subsystemXml;
        bootOperations = xmlParser.parse(subsystemXml);
        return this;
    }

    public void validateNotAlreadyBuilt() {
        if (built) {
            throw new IllegalStateException("Already built");
        }
    }

    public List<ModelNode> build() {
        built = true;
        return bootOperations;
    }

    public interface BootOperationParser {
        List<ModelNode> parse(String subsystemXml) throws XMLStreamException;
    }
}
