package org.keycloak.wildfly.xml.to.cli;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jboss.as.controller.Extension;
import org.keycloak.subsystem.adapter.extension.KeycloakExtension;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyXmlToCli {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalStateException("Need an input XML file");
        }
        File file = new File(args[0]);
        //File file = new File("/Users/kabir/sourcecontrol/keycloak/keycloak/adapters/oidc/wildfly/wildfly-subsystem/src/test/resources/org/keycloak/subsystem/adapter/extension/keycloak-1.1.xml");
        if (!file.exists()) {
            throw new IllegalStateException("Specified file does not exist: " + file);
        }

        URL url = WildFlyXmlToCli.class.getProtectionDomain().getCodeSource().getLocation();

        Path dest = Paths.get(url.toURI());
        dest = dest.resolve(WildFlyXmlToCli.class.getPackage().getName().replace('.', '/'));
        dest = dest.resolve(file.getName());
        if (Files.exists(dest)) {
            Files.delete(dest);
        }
        Files.copy(file.toPath(), dest);

        Extension extension = new KeycloakExtension();
        Worker worker = new Worker(KeycloakExtension.SUBSYSTEM_NAME, extension, file.getName());
        worker.convertXmlToCli();
    }
}
