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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.CapabilityReferenceRecorder;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.NotificationDefinition;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ProxyController;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.OverrideDescriptionProvider;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.extension.SubsystemInformation;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.AliasEntry;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.NotificationEntry;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.as.controller.registry.OperationEntry.Flag;
import org.jboss.as.controller.registry.RuntimePackageDependency;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLMapper;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
final class SubsystemTestDelegate {

    private static final String TEST_NAMESPACE = "urn.org.jboss.test:1.0";

    private static final ModelNode SUCCESS;
    static{
        SUCCESS = new ModelNode();
        SUCCESS.get(ModelDescriptionConstants.OUTCOME).set(ModelDescriptionConstants.SUCCESS);
        SUCCESS.get(ModelDescriptionConstants.RESULT);
        SUCCESS.protect();
    }

    private final List<KernelServices> kernelServices = new ArrayList<>();

    private final String mainSubsystemName;
    private final Extension mainExtension;

    /**
     * ExtensionRegistry we use just for registering parsers.
     * The ModelControllerService uses a separate registry. This is done this way to allow multiple ModelControllerService
     * instantiations in the same test without having to re-initialize the parsers.
     */
    private ExtensionRegistry extensionParsingRegistry;
    private ModelTestParser testParser;
    private XMLMapper xmlMapper;

    /**
     * Creates a new delegate.
     *
     * @param mainSubsystemName     the name of the subsystem
     * @param mainExtension         the extension to test
     */
    SubsystemTestDelegate(final String mainSubsystemName, final Extension mainExtension) {
        this.mainSubsystemName = mainSubsystemName;
        this.mainExtension = mainExtension;
    }

    String getMainSubsystemName() {
        return mainSubsystemName;
    }

    void initializeParser() throws Exception {
        //Initialize the parser
        xmlMapper = XMLMapper.Factory.create();
        extensionParsingRegistry = new ExtensionRegistry(getProcessType(), new RunningModeControl(RunningMode.NORMAL), null, null, null, RuntimeHostControllerInfoAccessor.SERVER);
        testParser = new TestParser(mainSubsystemName, extensionParsingRegistry);
        xmlMapper.registerRootElement(new QName(TEST_NAMESPACE, "test"), testParser);
        mainExtension.initializeParsers(extensionParsingRegistry.getExtensionParsingContext("Test", xmlMapper));
    }

    void cleanup() throws Exception {
        for (KernelServices kernelServices : this.kernelServices) {
            try {
                kernelServices.shutdown();
            } catch (Exception e) {
                //we don't care
            }
        }
        kernelServices.clear();
        xmlMapper = null;
        extensionParsingRegistry = null;
        testParser = null;
    }

    Extension getMainExtension() {
        return mainExtension;
    }

    /**
     * Parse the subsystem xml and create the operations that will be passed into the controller
     *
     * @param subsystemXml the subsystem xml to be parsed
     * @return the created operations
     * @throws XMLStreamException if there is a parsing problem
     */
    List<ModelNode> parse(String subsystemXml) throws XMLStreamException {
        String xml = "<test xmlns=\"" + TEST_NAMESPACE + "\">" +
                subsystemXml +
                "</test>";
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
        final List<ModelNode> operationList = new ArrayList<>();
        xmlMapper.parseDocument(operationList, reader);
        return operationList;
    }


    /**
     * Creates a new kernel services builder used to create a new controller containing the subsystem being tested
     *
     */
    KernelServicesBuilder createKernelServicesBuilder() {
        return new KernelServicesBuilderImpl();
    }

    /**
     * Gets the ProcessType to use when initializing the parsers. Defaults to {@link ProcessType#EMBEDDED_SERVER}
     *
     * @return the process type
     */
    ProcessType getProcessType() {
        return ProcessType.EMBEDDED_SERVER;
    }

    private ExtensionRegistry cloneExtensionRegistry() {
        final ExtensionRegistry clone = new ExtensionRegistry(ProcessType.STANDALONE_SERVER, new RunningModeControl(RunningMode.ADMIN_ONLY), null, null, null, RuntimeHostControllerInfoAccessor.SERVER);
        for (String extension : extensionParsingRegistry.getExtensionModuleNames()) {
            ExtensionParsingContext epc = clone.getExtensionParsingContext(extension, null);
            for (Map.Entry<String, SubsystemInformation> entry : extensionParsingRegistry.getAvailableSubsystems(extension).entrySet()) {
                for (String namespace : entry.getValue().getXMLNamespaces()) {
                    epc.setSubsystemXmlMapping(entry.getKey(), namespace, (XMLElementReader) null);
                }
            }
        }

        return clone;
    }

    private class KernelServicesBuilderImpl implements KernelServicesBuilder, BootOperationsBuilder.BootOperationParser {
        private final BootOperationsBuilder bootOperationBuilder;

        public KernelServicesBuilderImpl() {
            bootOperationBuilder = new BootOperationsBuilder(this);
        }

        @Override
        public KernelServicesBuilder setSubsystemXml(String subsystemXml) throws XMLStreamException {
            bootOperationBuilder.setXml(subsystemXml);
            return this;
        }


        public KernelServices build() throws Exception {
            bootOperationBuilder.validateNotAlreadyBuilt();
            List<ModelNode> bootOperations = bootOperationBuilder.build();
            KernelServicesImpl kernelServices = KernelServicesImpl.create(
                    mainSubsystemName, cloneExtensionRegistry(), bootOperations,
                    testParser, mainExtension, false);
            SubsystemTestDelegate.this.kernelServices.add(kernelServices);
            ImmutableManagementResourceRegistration subsystemReg = kernelServices.getRootRegistration().getSubModel(PathAddress.pathAddress(PathElement.pathElement(SUBSYSTEM, mainSubsystemName)));

            return kernelServices;
        }

        @Override
        public List<ModelNode> parse(String subsystemXml) throws XMLStreamException {
            return SubsystemTestDelegate.this.parse(subsystemXml);
        }
    }

    @SuppressWarnings("deprecation")
    private final ManagementResourceRegistration MOCK_RESOURCE_REG = new ManagementResourceRegistration() {

        @Override
        public PathAddress getPathAddress() {
            return PathAddress.EMPTY_ADDRESS;
        }

        @Override
        public ProcessType getProcessType() {
            return ProcessType.STANDALONE_SERVER;
        }

        @Override
        public ImmutableManagementResourceRegistration getParent() {
            return null;
        }

        @Override
        public boolean isRuntimeOnly() {
            return false;
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public OperationEntry getOperationEntry(PathAddress address, String operationName) {
            return null;
        }

        @Override
        public OperationStepHandler getOperationHandler(PathAddress address, String operationName) {
            return null;
        }

        @Override
        public DescriptionProvider getOperationDescription(PathAddress address, String operationName) {
            return null;
        }

        @Override
        public Set<Flag> getOperationFlags(PathAddress address, String operationName) {
            return null;
        }

        @Override
        public Set<String> getAttributeNames(PathAddress address) {
            return null;
        }

        @Override
        public AttributeAccess getAttributeAccess(PathAddress address, String attributeName) {
            return null;
        }

        @Override
        public Map<String, AttributeAccess> getAttributes(PathAddress address) {
            return Collections.emptyMap();
        }

        @Override
        public Set<String> getChildNames(PathAddress address) {
            return null;
        }

        @Override
        public Set<PathElement> getChildAddresses(PathAddress address) {
            return null;
        }

        @Override
        public DescriptionProvider getModelDescription(PathAddress address) {
            return null;
        }

        @Override
        public Map<String, OperationEntry> getOperationDescriptions(PathAddress address, boolean inherited) {
            return null;
        }

        @Override
        public Map<String, NotificationEntry> getNotificationDescriptions(PathAddress address, boolean inherited) {
            return null;
        }

        @Override
        public ProxyController getProxyController(PathAddress address) {
            return null;
        }

        @Override
        public Set<ProxyController> getProxyControllers(PathAddress address) {
            return null;
        }

        @Override
        public ManagementResourceRegistration getOverrideModel(String name) {
            return null;
        }

        @Override
        public ManagementResourceRegistration getSubModel(PathAddress address) {
            if (address.size() == 0) {
                return MOCK_RESOURCE_REG;
            } else if (address.size() == 1) {
                PathElement pe = address.getElement(0);
                String key = pe.getKey();
                if (pe.isWildcard()
                        && (ModelDescriptionConstants.PROFILE.equals(key) || ModelDescriptionConstants.DEPLOYMENT.equals(key))) {
                    return MOCK_RESOURCE_REG;
                }
            }
            return null;
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            return Collections.emptyList();
        }

        @Override
        public ManagementResourceRegistration registerSubModel(ResourceDefinition resourceDefinition) {
            return MOCK_RESOURCE_REG;
        }

        @Override
        public void unregisterSubModel(PathElement address) {
        }

        @Override
        public boolean isAllowsOverride() {
            return true;
        }

        @Override
        public void setRuntimeOnly(boolean runtimeOnly) {
        }

        @Override
        public ManagementResourceRegistration registerOverrideModel(String name, OverrideDescriptionProvider descriptionProvider) {
            return MOCK_RESOURCE_REG;
        }

        @Override
        public void unregisterOverrideModel(String name) {
        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler) {

        }

        @Override
        public void registerOperationHandler(OperationDefinition definition, OperationStepHandler handler, boolean inherited) {

        }

        @Override
        public void registerCapability(RuntimeCapability capability) {

        }

        @Override
        public void registerIncorporatingCapabilities(Set<RuntimeCapability> capabilities) {

        }

        @Override
        public void registerRequirements(Set<CapabilityReferenceRecorder> requirements) {

        }

        @Override
        public Set<RuntimeCapability> getCapabilities() {
            return Collections.emptySet();
        }

        @Override
        public Set<RuntimeCapability> getIncorporatingCapabilities() {
            return null;
        }

        @Override
        public Set<CapabilityReferenceRecorder> getRequirements() {
            return Collections.emptySet();
        }

        @Override
        public void unregisterOperationHandler(String operationName) {

        }

        @Override
        public void registerReadWriteAttribute(AttributeDefinition definition, OperationStepHandler readHandler, OperationStepHandler writeHandler) {
        }

        @Override
        public void registerReadOnlyAttribute(AttributeDefinition definition, OperationStepHandler readHandler) {
        }

        @Override
        public void registerMetric(AttributeDefinition definition, OperationStepHandler metricHandler) {
        }

        @Override
        public void unregisterAttribute(String attributeName) {
        }

        @Override
        public void registerNotification(NotificationDefinition notification, boolean inherited) {
            // no-op
        }

        @Override
        public void registerNotification(NotificationDefinition notification) {
            // no-op
        }

        @Override
        public void unregisterNotification(String notificationType) {
            // no-op
        }

        @Override
        public boolean isOrderedChildResource() {
            return false;
        }

        @Override
        public Set<String> getOrderedChildTypes() {
            return Collections.emptySet();
        }

        @Override
        public void registerProxyController(PathElement address, ProxyController proxyController) {
        }

        @Override
        public void unregisterProxyController(PathElement address) {
        }

        @Override
        public void registerAlias(PathElement address, AliasEntry alias) {
        }

        @Override
        public void unregisterAlias(PathElement address) {
        }

        @Override
        public AliasEntry getAliasEntry() {
            return null;
        }

        @Override
        public boolean isAlias() {
            return false;
        }

        @Override
        public void registerAdditionalRuntimePackages(RuntimePackageDependency... pkgs) {
            // no-op
        }

        @Override
        public Set<RuntimePackageDependency> getAdditionalRuntimePackages() {
            return Collections.emptySet();
        }
    };


}
