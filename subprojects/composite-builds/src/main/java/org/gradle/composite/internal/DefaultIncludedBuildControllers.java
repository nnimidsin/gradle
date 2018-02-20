/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.composite.internal;

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;

import java.util.Map;

class DefaultIncludedBuildControllers implements Stoppable, IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = Maps.newHashMap();
    private final ManagedExecutor executorService;
    private final BuildOperationExecutor buildOperationExecutor;
    private final IncludedBuildRegistry includedBuildRegistry;
    private boolean taskExecutionStarted;

    DefaultIncludedBuildControllers(ExecutorFactory executorFactory, IncludedBuildRegistry includedBuildRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.includedBuildRegistry = includedBuildRegistry;
        this.executorService = executorFactory.create("included builds");
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        IncludedBuild build = includedBuildRegistry.getBuild(buildId);

        DefaultIncludedBuildController newBuildController = new DefaultIncludedBuildController(build);
        buildControllers.put(buildId, newBuildController);
        executorService.submit(newBuildController);

        // Required for build controllers created after initial start
        if (taskExecutionStarted) {
            newBuildController.startTaskExecution(buildOperationExecutor.getCurrentOperation());
        }

        return newBuildController;
    }

    @Override
    public void startTaskExecution() {
        this.taskExecutionStarted = true;
        final BuildOperationState currentOperation = buildOperationExecutor.getCurrentOperation();

        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                populateTaskGraphs();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Calculate task graphs of included builds").parent(currentOperation);
            }
        });

        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.startTaskExecution(currentOperation);
        }
    }

    private void populateTaskGraphs() {
        boolean tasksDiscovered = true;
        while (tasksDiscovered) {
            tasksDiscovered = false;
            for (IncludedBuildController buildController : buildControllers.values()) {
                if (buildController.populateTaskGraph()) {
                    tasksDiscovered = true;
                }
            }
        }
    }

    @Override
    public void stopTaskExecution() {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.stopTaskExecution();
        }
        buildControllers.clear();
        for (IncludedBuild includedBuild : includedBuildRegistry.getIncludedBuilds()) {
            ((IncludedBuildInternal) includedBuild).finishBuild();
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
        executorService.stop();
    }
}
