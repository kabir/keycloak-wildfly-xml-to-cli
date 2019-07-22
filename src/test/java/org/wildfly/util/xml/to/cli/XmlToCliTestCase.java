package org.wildfly.util.xml.to.cli;

import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.util.xml.to.cli.subsystem.SimpleSubsystemExtension;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class XmlToCliTestCase {

    @Test
    public void testXmlToCli() throws Exception {
        String subsystemXml =
                "<subsystem xmlns=\"" + SimpleSubsystemExtension.NAMESPACE + "\">" +
                "</subsystem>";

        WildFlyXmlToCli util = WildFlyXmlToCli.builder()
                .setXml(subsystemXml)
                .setExtension(new SimpleSubsystemExtension())
                .setSubsystemName("mysubsystem")
                .build();

        List<ModelNode> operations = util.convertXmlToOperations();

        Assert.assertEquals(1, operations.size());

        ModelNode op = operations.get(0);
        Assert.assertEquals(2, op.keys().size());
        Assert.assertEquals("add", op.get("operation").asString());
        Assert.assertEquals(PathAddress.pathAddress("subsystem", "mysubsystem"), PathAddress.pathAddress(op.get("address")));
    }
}
