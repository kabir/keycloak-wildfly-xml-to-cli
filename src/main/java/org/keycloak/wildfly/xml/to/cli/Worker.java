package org.keycloak.wildfly.xml.to.cli;

import java.nio.file.Path;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class Worker extends AbstractSubsystemTest {

    private final String xmlName;

    public Worker(String mainSubsystemName, Extension mainExtension, String xmlName) {
        super(mainSubsystemName, mainExtension);
        this.xmlName = xmlName;
    }

    void convertXmlToCli() throws Exception {
        initializeParser();
        final KernelServices services =
                super.createKernelServicesBuilder(AdditionalInitialization.MANAGEMENT).setSubsystemXmlResource(xmlName).build();
        if (!services.isSuccessfulBoot()) {
            throw new IllegalStateException("The XML does not appear to be valid.");
        }

        ModelNode op = Util.createOperation("describe", PathAddress.pathAddress("subsystem", getMainSubsystemName()));
        ModelNode result = services.executeForResult(op);
        System.out.println(result);

        cleanup();
    }
}
