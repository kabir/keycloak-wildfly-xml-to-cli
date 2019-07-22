package org.wildfly.util.xml.to.cli.impl;

import java.util.List;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class Worker  {

    private final SubsystemTestDelegate delegate;
    private final String xml;


    public Worker(String mainSubsystemName, Extension mainExtension, String xml) {
        this.delegate = new SubsystemTestDelegate(mainSubsystemName, mainExtension);
        this.xml = xml;
    }

    public List<ModelNode> convertXmlToOperations() throws Exception {
        delegate.initializeParser();
        final KernelServices services =
                delegate.createKernelServicesBuilder().setSubsystemXml(xml).build();
        if (!services.isSuccessfulBoot()) {
            throw new IllegalStateException("The XML does not appear to be valid.");
        }

        ModelNode op = Util.createOperation("describe", PathAddress.pathAddress("subsystem", delegate.getMainSubsystemName()));
        ModelNode result = services.executeForResult(op);

        return result.asList();
    }
}
