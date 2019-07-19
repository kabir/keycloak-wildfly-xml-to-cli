package org.keycloak.wildfly.xml.to.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.jboss.as.controller.Extension;
import org.keycloak.subsystem.adapter.extension.KeycloakExtension;
import org.keycloak.subsystem.adapter.saml.extension.KeycloakSamlExtension;

/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 */
public class WildFlyXmlToCli {

    private final File keycloak;
    private final File kcSaml;
    private final String kcVersion;
    private final String kcSamlVersion;

    private WildFlyXmlToCli(File keycloak, File kcSaml, String kcVersion, String kcSamlVersion) {
        this.keycloak = keycloak;
        this.kcSaml = kcSaml;
        this.kcVersion = kcVersion;
        this.kcSamlVersion = kcSamlVersion;
    }

    static WildFlyXmlToCli create(String keycloak, String kcSaml, String kcVersion, String kcSamlVersion) {
        File kcFile = null;
        File kcSamlFile = null;
        if (keycloak != null && keycloak.trim().length() > 0) {
            kcFile = new File(keycloak);
            if (!kcFile.exists()) {
                throw new IllegalStateException("Specified keycloak file does not exist: " + kcFile.getAbsolutePath());
            }
            if (kcVersion == null) {
                throw new IllegalStateException("You specified a keycloak file without specifying the namespace version");
            }
        }
        if (kcSaml != null) {
            kcSamlFile = new File(kcSaml);
            if (!kcSamlFile.exists()) {
                throw new IllegalStateException("Specified keycloak-saml file does not exist: " + kcSamlFile.getAbsolutePath());
            }
            if (kcSamlVersion == null) {
                throw new IllegalStateException("You specified a keycloak-saml file without specifying the namespace version");
            }
        }
        return new WildFlyXmlToCli(kcFile, kcSamlFile, kcVersion, kcSamlVersion);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalStateException("Need: " +
                    "<keycloak xml path> " +
                    "<keycloak-saml xml path> " +
                    "<keycloak subsystem namespace version> " +
                    "<keycloak-saml subsystem version>");
        }


        WildFlyXmlToCli util = WildFlyXmlToCli.create(args[0], args[1], args[2], args[3]);
        String cli = util.run();

        System.out.println();
        System.out.println("# ========= CLI COMMANDS ==========");

        System.out.println(cli);

        System.out.println("# ============== END ==============");
        System.out.println();

        // We seem to need this when running using the exec maven plugin
        // Hopefully it will not be a problem when running somewhere else...
        System.exit(0);

    }

    public String run() throws Exception {
        StringBuilder sb = new StringBuilder();
        if (keycloak != null) {
            String commands =
                    run(keycloak,
                            KeycloakExtension.SUBSYSTEM_NAME,
                            "urn:jboss:domain:keycloak:" + kcVersion,
                            new KeycloakExtension());
            if (commands.length() > 0) {
                sb.append(commands);
                sb.append("\n");
            }
        }

        if (kcSaml != null) {
            String commands =
                    run(kcSaml,
                            KeycloakSamlExtension.SUBSYSTEM_NAME,
                            "urn:jboss:domain:keycloak-saml:" + kcSamlVersion,
                            new KeycloakSamlExtension());
            if (commands.length() > 0) {
                sb.append(commands);
                sb.append("\n");
            }
        }

        String commands =
                "\nbatch\n" +
                sb.toString() +
                "\nrun-batch\n";

        return commands;
    }

    private String run(File file, String subsystemName, String xmlNameSpace, Extension extension) throws Exception {
        String xml = loadXml(file);
        xml = "<subsystem xmlns=\"" + xmlNameSpace + "\">\n" +
                xml +
                "\n</subsystem>";
        Worker worker = new Worker(subsystemName, extension, xml);
        return worker.convertXmlToCli();
    }

    private String loadXml(File file) throws IOException {
        URL configURL = file.toURI().toURL();

        StringWriter writer = new StringWriter();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(configURL.openStream(), StandardCharsets.UTF_8))){
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.write("\n");
            }
        }
        return writer.toString();
    }
}
