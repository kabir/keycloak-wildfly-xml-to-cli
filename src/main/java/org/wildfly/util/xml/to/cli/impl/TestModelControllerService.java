package org.wildfly.util.xml.to.cli.impl;


import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.jboss.as.controller.AbstractControllerService;
import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.CompositeOperationHandler;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ManagementModel;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.RunningModeControl;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.access.management.AccessConstraintDefinition;
import org.jboss.as.controller.access.management.DelegatingConfigurableAuthorizer;
import org.jboss.as.controller.access.management.ManagementSecurityIdentitySupplier;
import org.jboss.as.controller.audit.AuditLogger;
import org.jboss.as.controller.client.OperationAttachments;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.NonResolvingResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.extension.ExtensionRegistryType;
import org.jboss.as.controller.operations.global.GlobalNotifications;
import org.jboss.as.controller.operations.global.GlobalOperationHandlers;
import org.jboss.as.controller.persistence.ConfigurationPersistenceException;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.repository.ContentReference;
import org.jboss.as.repository.ContentRepository;
import org.jboss.as.server.DeployerChainAddHandler;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.as.server.controller.resources.ServerDeploymentResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.vfs.VirtualFile;

/**
 * Internal class used by test framework.Boots up the model controller used for the test.
 * While the super class {@link AbstractControllerService} exists here in the main code source, for the legacy controllers it is got from the
 * xxxx/test-controller-xxx jars instead (see the constructor javadocs for more information)
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
//TODO find better way to support legacy TestModelControllerService without need for having all old methods still present on AbstractControllerService
class TestModelControllerService extends AbstractControllerService implements ControllerInitializer.TestControllerAccessor {

    private final CountDownLatch latch = new CountDownLatch(1);
    private final StringConfigurationPersister persister;
    private final RunningModeControl runningModeControl;
    private volatile ManagementResourceRegistration rootRegistration;
    private volatile Throwable error;
    private volatile boolean bootSuccess;

    private final Extension mainExtension;
    private final ControllerInitializer controllerInitializer;
    private final ExtensionRegistry extensionRegistry;
    private final ContentRepository contentRepository = new MockContentRepository();

    /**
     * This is the constructor to use for current subsystem tests
     */
    TestModelControllerService(
            final Extension mainExtension,
            final ControllerInitializer controllerInitializer,
            final ExtensionRegistry extensionRegistry,
            final RunningModeControl runningModeControl,
            final StringConfigurationPersister persister,
            final ResourceDefinition resourceDefinition,
            final CapabilityRegistry capabilityRegistry) {
        super(ProcessType.STANDALONE_SERVER, runningModeControl, persister, new ControlledProcessState(true),
                resourceDefinition, null, ExpressionResolver.TEST_RESOLVER, AuditLogger.NO_OP_LOGGER,
                new DelegatingConfigurableAuthorizer(), new ManagementSecurityIdentitySupplier(), capabilityRegistry);
        this.persister = persister;
        this.runningModeControl = runningModeControl;
        this.mainExtension = mainExtension;
        this.extensionRegistry = extensionRegistry;
        this.controllerInitializer = controllerInitializer;
    }

    static TestModelControllerService create(
            final Extension mainExtension,
            final ControllerInitializer controllerInitializer,
            final ExtensionRegistry extensionRegistry,
            final StringConfigurationPersister persister,
            CapabilityRegistry capabilityRegistry) {
        return new TestModelControllerService(
                mainExtension,
                controllerInitializer,
                extensionRegistry,
                new RunningModeControl(RunningMode.ADMIN_ONLY),
                persister,
                new SimpleResourceDefinition(null, NonResolvingResourceDescriptionResolver.INSTANCE),
                capabilityRegistry);
    }


    public boolean isSuccessfulBoot() {
        return bootSuccess;
    }

    public Throwable getBootError() {
        return error;
    }

    RunningMode getRunningMode() {
        return runningModeControl.getRunningMode();
    }

    ProcessType getProcessType() {
        return processType;
    }

    @Override
    protected void initModel(ManagementModel managementModel, Resource modelControllerResource) {
        this.rootRegistration = managementModel.getRootResourceRegistration();
        initCoreModel(managementModel, modelControllerResource);
        initExtraModel(managementModel);
    }

    private void initCoreModel(ManagementModel managementModel, Resource modelControllerResource) {
        GlobalOperationHandlers.registerGlobalOperations(rootRegistration, ProcessType.STANDALONE_SERVER);
        rootRegistration.registerOperationHandler(CompositeOperationHandler.DEFINITION, CompositeOperationHandler.INSTANCE);
    }

    protected void initExtraModel(ManagementModel managementModel) {
        Resource rootResource = managementModel.getRootResource();
        ManagementResourceRegistration rootRegistration = managementModel.getRootResourceRegistration();
        initExtraModelInternal(rootResource, rootRegistration);
        // register the global notifications so there is no warning that emitted notifications are not described by the resource.
        GlobalNotifications.registerGlobalNotifications(managementModel.getRootResourceRegistration(), processType);
    }

    private void initExtraModelInternal(Resource rootResource, ManagementResourceRegistration rootRegistration) {
        rootResource.getModel().get(SUBSYSTEM);

        rootRegistration.registerSubModel(ServerDeploymentResourceDefinition.create(contentRepository, null, null));

        controllerInitializer.setTestModelControllerAccessor(this);
        controllerInitializer.initializeModel(rootResource, rootRegistration);
    }

    @Override
    protected boolean boot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) throws ConfigurationPersistenceException {
        try {
            preBoot(bootOperations, rollbackOnRuntimeFailure);

            bootSuccess = super.boot(persister.getBootOperations(), rollbackOnRuntimeFailure);

            return bootSuccess;
        } catch (Exception e) {
            error = e;
        } catch (Throwable t) {
            error = new Exception(t);
        } finally {
            postBoot();
        }
        return false;
    }

    protected void preBoot(List<ModelNode> bootOperations, boolean rollbackOnRuntimeFailure) {
        mainExtension.initialize(extensionRegistry.getExtensionContext("Test", getRootRegistration(), ExtensionRegistryType.SLAVE));
    }

    protected void postBoot() {
        DeployerChainAddHandler.INSTANCE.clearDeployerMap();
    }

    @Override
    protected void bootThreadDone() {
        try {
            super.bootThreadDone();
        } finally {
            countdownDoneLatch();
        }
    }

    protected void countdownDoneLatch() {
        latch.countDown();
    }

    @Override
    public void start(StartContext context) throws StartException {
        try {
            super.start(context);
        } catch (StartException e) {
            error = e;
            e.printStackTrace();
            latch.countDown();
            throw e;
        } catch (Throwable t) {
            error = t;
            latch.countDown();
            throw new StartException(t);
        }
    }

    public void waitForSetup() throws Exception {
        latch.await();
        if (error != null) {
            if (error instanceof Exception)
                throw (Exception) error;
            throw new RuntimeException(error);
        }
    }

    public ManagementResourceRegistration getRootRegistration() {
        return rootRegistration;
    }

    @SuppressWarnings("deprecation")
    @Override
    protected ModelNode internalExecute(final ModelNode operation, final OperationMessageHandler handler,
                                        final ModelController.OperationTransactionControl control,
                                        final OperationAttachments attachments, final OperationStepHandler prepareStep) {
        return super.internalExecute(operation, handler, control, attachments, prepareStep);
    }

    private void delete(File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                delete(child);
            }
        }
        file.delete();
    }


    public ServerEnvironment getServerEnvironment() {
        Properties props = new Properties();
        File home = new File("target/jbossas");
        delete(home);
        home.mkdir();
        props.put(ServerEnvironment.HOME_DIR, home.getAbsolutePath());

        File standalone = new File(home, "standalone");
        standalone.mkdir();
        props.put(ServerEnvironment.SERVER_BASE_DIR, standalone.getAbsolutePath());

        File configuration = new File(standalone, "configuration");
        configuration.mkdir();
        props.put(ServerEnvironment.SERVER_CONFIG_DIR, configuration.getAbsolutePath());

        File xml = new File(configuration, "standalone.xml");
        try {
            xml.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        props.put(ServerEnvironment.JBOSS_SERVER_DEFAULT_CONFIG, "standalone.xml");

        return new ServerEnvironment(null, props, new HashMap<String, String>(), "standalone.xml", null, ServerEnvironment.LaunchType.STANDALONE, runningModeControl.getRunningMode(), null, false);
    }
    public static class DelegatingResourceDefinition implements ResourceDefinition {
        private volatile ResourceDefinition delegate;

        public void setDelegate(ResourceDefinition delegate) {
            this.delegate = delegate;
        }

        @Override
        public void registerOperations(ManagementResourceRegistration resourceRegistration) {
            delegate.registerOperations(resourceRegistration);
        }

        @Override
        public void registerChildren(ManagementResourceRegistration resourceRegistration) {
            delegate.registerChildren(resourceRegistration);
        }

        @Override
        public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
            delegate.registerAttributes(resourceRegistration);
        }

        @Override
        public void registerNotifications(ManagementResourceRegistration resourceRegistration) {
            delegate.registerNotifications(resourceRegistration);
        }

        @Override
        public PathElement getPathElement() {
            return delegate.getPathElement();
        }

        @Override
        public DescriptionProvider getDescriptionProvider(ImmutableManagementResourceRegistration resourceRegistration) {
            return delegate.getDescriptionProvider(resourceRegistration);
        }

        @Override
        public List<AccessConstraintDefinition> getAccessConstraints() {
            if (delegate == null) {
                return Collections.emptyList();
            }
            return delegate.getAccessConstraints();
        }

        @Override
        public boolean isRuntime() {
            return delegate.isRuntime();
        }

        @Override
        public boolean isOrderedChild() {
            if (delegate == null) {
                return false;
            }
            return delegate.isOrderedChild();
        }

        @Override
        public void registerCapabilities(ManagementResourceRegistration resourceRegistration) {
            delegate.registerCapabilities(resourceRegistration);
        }
    }

    private static class MockContentRepository implements ContentRepository {

        @Override
        public byte[] addContent(InputStream stream) throws IOException {
            return null;
        }

        @Override
        public VirtualFile getContent(byte[] hash) {
            return null;
        }

        @Override
        public boolean hasContent(byte[] hash) {
            return false;
        }

        @Override
        public boolean syncContent(ContentReference reference) {
            return false;
        }

        @Override
        public void removeContent(ContentReference reference) {
        }

        @Override
        public void addContentReference(ContentReference reference) {
        }

        @Override
        public Map<String, Set<String>> cleanObsoleteContent() {
            return null;
        }
    }
}
