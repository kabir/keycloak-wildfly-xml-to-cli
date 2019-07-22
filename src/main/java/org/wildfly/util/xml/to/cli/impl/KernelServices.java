/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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

import java.io.InputStream;

import org.jboss.as.controller.ModelController.OperationTransactionControl;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceContainer;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
interface KernelServices {

    /**
     * Get whether the controller booted successfully
     * @return true if the controller booted successfully
     */
    boolean isSuccessfulBoot();

    /**
     * Get any errors thrown on boot
     * @return the boot error
     */
    Throwable getBootError();

    /**
     * Gets the service container
     *
     * @return the service container
     */
    ServiceContainer getContainer();

    /**
     * Execute an operation in the model controller
     *
     * @param operation the operation to execute
     * @param inputStreams Input Streams for the operation
     * @return the whole result of the operation
     */
    ModelNode executeOperation(ModelNode operation, InputStream... inputStreams);

    ModelNode executeOperation(ModelNode operation, OperationTransactionControl txControl);

    ModelNode executeForResult(ModelNode operation, InputStream... inputStreams) throws OperationFailedException;

    void shutdown();

    ImmutableManagementResourceRegistration getRootRegistration();

}