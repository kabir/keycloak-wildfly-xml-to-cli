package org.wildfly.util.xml.to.cli.impl;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.controller.CapabilityRegistry;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ModelController;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.Operation;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.as.server.Services;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

/**
 * Allows access to the service container and the model controller
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class KernelServicesImpl implements KernelServices {

    private volatile ServiceContainer container;
    private final TestModelControllerService controllerService;
    private final ModelController controller;
    private final StringConfigurationPersister persister;
    private final ManagementResourceRegistration rootRegistration;
    protected final String mainSubsystemName;
    protected final ExtensionRegistry extensionRegistry;
    private final boolean successfulBoot;
    private final Throwable bootError;

    private static final AtomicInteger counter = new AtomicInteger();


    protected KernelServicesImpl(ServiceContainer container,
                                 TestModelControllerService controllerService,
                                 StringConfigurationPersister persister,
                                 ManagementResourceRegistration rootRegistration,
                                 String mainSubsystemName,
                                 ExtensionRegistry extensionRegistry,
                                 boolean successfulBoot,
                                 Throwable bootError) {
        this.container = container;
        this.controllerService = controllerService;
        this.controller = controllerService.getValue();
        this.persister = persister;
        this.rootRegistration = rootRegistration;

        this.mainSubsystemName = mainSubsystemName;
        this.extensionRegistry = extensionRegistry;
        this.successfulBoot = successfulBoot;
        this.bootError = bootError;
    }

    public static KernelServicesImpl create(
            String mainSubsystemName,
            ExtensionRegistry controllerExtensionRegistry,
            List<ModelNode> bootOperations,
            ModelTestParser testParser,
            Extension mainExtension,
            boolean persistXml) throws Exception {
        ControllerInitializer controllerInitializer = new ControllerInitializer();

        PathManagerService pathManager = new PathManagerService() {
        };

        controllerInitializer.setPathManger(pathManager);

        //Initialize the controller
        ServiceContainer container = ServiceContainer.Factory.create("subsystem-test" + counter.incrementAndGet());
        ServiceTarget target = container.subTarget();
        List<ModelNode> extraOps = controllerInitializer.initializeBootOperations();
        List<ModelNode> allOps = new ArrayList<ModelNode>();
        if (extraOps != null) {
            allOps.addAll(extraOps);
        }
        allOps.addAll(bootOperations);
        StringConfigurationPersister persister = new StringConfigurationPersister(allOps, testParser, persistXml);
        controllerExtensionRegistry.setWriterRegistry(persister);
        controllerExtensionRegistry.setPathManager(pathManager);


        CapabilityRegistry capabilityRegistry = new CapabilityRegistry(true);
        //Use the default implementation of test controller for the main controller, and for tests that don't have another one set up on the classpath
        TestModelControllerFactory testModelControllerFactory = new TestModelControllerFactory() {

            @Override
            public TestModelControllerService create(Extension mainExtension, ControllerInitializer controllerInitializer, ExtensionRegistry extensionRegistry, StringConfigurationPersister persister) {
                return TestModelControllerService.create(mainExtension, controllerInitializer, extensionRegistry, persister, capabilityRegistry);
            }

        };

        TestModelControllerService svc = testModelControllerFactory.create(mainExtension, controllerInitializer, controllerExtensionRegistry, persister);
        ServiceBuilder<ModelController> builder = target.addService(Services.JBOSS_SERVER_CONTROLLER, svc);
        ServiceName pmSvcName = ServiceName.parse("org.wildfly.management.path-manager"); // we can't reference the capability directly as it's not present in legacy controllers
        addDependencyViaReflection(builder, pmSvcName); // ensure this is up before the ModelControllerService, as it would be in a real server
        builder.install();
        target.addService(pmSvcName, pathManager).addAliases(PathManagerService.SERVICE_NAME).install();

        //sharedState = svc.state;
        svc.waitForSetup();
        //processState.setRunning();

        return new KernelServicesImpl(container, svc, persister, svc.getRootRegistration(), mainSubsystemName, controllerExtensionRegistry, svc.isSuccessfulBoot(), svc.getBootError());
    }

    private static void addDependencyViaReflection(final ServiceBuilder builder, final ServiceName dependencyName) {
        // FYI We are using reflective code here because KernelServicesImpl is used in transformer tests
        // which are testing legacy EAP distributions that didn't have MSC with ServiceBuilder.requires() method.
        try {
            // use requires() first
            final Method requiresMethod = ServiceBuilder.class.getMethod("requires", ServiceName.class);
            requiresMethod.invoke(builder, new Object[] {dependencyName});
            return;
        } catch (Throwable ignored) {}
        try {
            // use addDependency() second
            final Method addDependencyMethod = ServiceBuilder.class.getMethod("addDependency", ServiceName.class);
            addDependencyMethod.invoke(builder, new Object[] {dependencyName});
            return;
        } catch (Throwable ignored) {}
        throw new IllegalStateException();
    }

    ExtensionRegistry getExtensionRegistry() {
        return extensionRegistry;
    }

    @Override
    public boolean isSuccessfulBoot() {
        return successfulBoot;
    }

    @Override
    public Throwable getBootError() {
        return bootError;
    }

    @Override
    public ServiceContainer getContainer() {
        return container;
    }

    /**
     * Execute an operation in the model controller
     *
             * @param operation the operation to execute
     * @param inputStreams Input Streams for the operation
     * @return the whole result of the operation
     */
    @Override
    public ModelNode executeOperation(ModelNode operation, InputStream...inputStreams) {
        if (inputStreams.length == 0) {
            return executeOperation(operation, ModelController.OperationTransactionControl.COMMIT);
        } else {
            ExecutorService executor = Executors.newCachedThreadPool();
            try {
                ModelControllerClient client = controller.createClient(executor);
                OperationBuilder builder = OperationBuilder.create(operation);
                for (InputStream in : inputStreams) {
                    builder.addInputStream(in);
                }
                Operation op = builder.build();

                try {
                    return client.execute(op);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Override
    public ModelNode executeOperation(ModelNode operation, ModelController.OperationTransactionControl txControl) {
        return controller.execute(operation, null, txControl, null);
    }

    @Override
    public ModelNode executeForResult(ModelNode operation, InputStream...inputStreams) throws OperationFailedException {
        ModelNode rsp = executeOperation(operation, inputStreams);
        if (FAILED.equals(rsp.get(OUTCOME).asString())) {
            ModelNode fd = rsp.get(FAILURE_DESCRIPTION);
            throw new OperationFailedException(fd.toString(), fd);
        }
        return rsp.get(RESULT);
    }

    @Override
    public void shutdown() {
        if (container != null) {
            container.shutdown();
            try {
                container.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            container = null;

        }
    }

    @Override
    public ImmutableManagementResourceRegistration getRootRegistration() {
        return rootRegistration;
    }
}
