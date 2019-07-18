package org.keycloak.wildfly.xml.to.cli;

import java.nio.file.Path;
import java.util.Iterator;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

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


        StringBuilder sb = new StringBuilder();
        sb.append("batch\n");
        for (ModelNode addOp : result.asList()) {
            sb.append(createCLIOperation(addOp));
            sb.append("\n");
        }
        sb.append("run-batch");

        System.out.println("========= CLI COMMANDS =========");

        System.out.println(sb.toString());

        System.out.println("============== END ==============");

        cleanup();
        System.exit(0);
    }


    private String createCLIOperation(ModelNode operation) {
        ModelNode opNameNode = operation.remove("operation");
        ModelNode addrNode = operation.remove("address");

        StringBuilder sb = new StringBuilder();
        // TODO change back when https://issues.jboss.org/browse/WFCORE-4570 is fixed
        //sb.append(PathAddress.pathAddress(addrNode).toCLIStyleString());
        sb.append(createCLIAddress(PathAddress.pathAddress(addrNode)));
        sb.append(":");
        sb.append(opNameNode.asString());
        sb.append("(");
        sb.append(createParameters(operation));
        sb.append(")");

        return sb.toString();
    }

    private String createCLIAddress(PathAddress address) {
        // We could have used PathAddress.toCLIStyleString() but keycloak uses some strange path element
        // values in some cases, which need quoting. So to be safe we quote values containing
        // '/' or '='
        // https://issues.jboss.org/browse/WFCORE-4570
        StringBuilder sb = new StringBuilder();

        for (Iterator<PathElement> it = address.iterator() ; it.hasNext() ; ) {
            PathElement element = it.next();
            sb.append("/");
            sb.append(element.getKey());
            sb.append("=");
            boolean quote = element.getValue().contains("/") || element.getValue().contains("=");
            if (quote) {
                sb.append("\"");
            }
            sb.append(element.getValue());
            if (quote) {
                sb.append("\"");
            }
        }
        return sb.toString();
    }

    private String createParameters(ModelNode operation) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        for (String key : operation.keys()) {
            ModelNode valueNode = operation.get(key);
            if (!valueNode.isDefined()) {
                continue;
            }

            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(key);
            sb.append("=");
            sb.append(createParameterValue(valueNode));
        }
        return sb.toString();
    }

    private String createParameterValue(ModelNode valueNode) {
        StringBuilder sb = new StringBuilder();
        final boolean complexType = ModelType.OBJECT.equals(valueNode.getType()) || ModelType.LIST.equals(valueNode.getType())
                || ModelType.PROPERTY.equals(valueNode.getType());
        final String strValue = valueNode.asString();
        if (!complexType) {
            sb.append("\"");
            if (!strValue.isEmpty() && strValue.charAt(0) == '$') {
                sb.append('\\');
            }
        }
        sb.append(strValue);
        if (!complexType) {
            sb.append('"');
        }
        return sb.toString();
    }

}
