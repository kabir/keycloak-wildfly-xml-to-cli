package org.wildfly.util.xml.to.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.util.xml.to.cli.impl.Worker;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyXmlToCli {

    private final String subsystemName;
    private final String xml;
    private final Extension extension;


    private WildFlyXmlToCli(Builder builder) {
        this.subsystemName = builder.subsystemName;
        this.xml = builder.xml;
        this.extension = builder.extension;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String subsystemName;
        private String xml;
        private Extension extension;

        private Builder() {
        }

        public Builder setSubsystemName(String subsystemName) {
            this.subsystemName = subsystemName;
            return this;
        }

        public Builder setXml(String xml) {
            this.xml = xml;
            return this;
        }

        public Builder setXmlFile(String path) throws IOException {
            return setXmlFile(new File(path));
        }

        public Builder setXmlFile(File file) throws IOException {
            if (!file.exists()) {
                throw new IllegalStateException("Specified keycloak file does not exist: " + file.getAbsolutePath());
            }
            URL configURL = file.toURI().toURL();

            StringWriter writer = new StringWriter();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream(), StandardCharsets.UTF_8))){
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.write("\n");
                }
            }
            this.xml = writer.toString();
            return this;
        }

        public Builder setExtension(Extension extension) {
            this.extension = extension;
            return this;
        }

        public WildFlyXmlToCli build() {
            if (subsystemName == null) {
                throw new IllegalStateException("No subsystem name set");
            }
            if (xml == null) {
                throw new IllegalStateException("No xml set");
            }
            if (extension == null) {
                throw new IllegalStateException("No extension set");
            }
            return new WildFlyXmlToCli(this);
        }
    }

    public List<ModelNode> convertXmlToOperations() throws Exception {
        Worker worker = new Worker(subsystemName, extension, xml);
        return worker.convertXmlToCli();
    }

    public String convertXmlToCli() throws Exception {
        List<ModelNode> operations = convertXmlToOperations();
        StringBuilder sb = new StringBuilder();
        for (ModelNode addOp : operations) {
            sb.append(createCLIOperation(addOp));
            sb.append("\n\n");
        }

        return sb.toString();
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

        for (Iterator<PathElement> it = address.iterator(); it.hasNext() ; ) {
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
