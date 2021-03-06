/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import java.io.File;

public class VisualStudioProjectConfiguration {
    public final static String ARTIFACT_TYPE = "visualStudioProjectConfiguration";

    private final DefaultVisualStudioProject vsProject;
    private final String name;
    private final String configurationName;
    private final String platformName = "Win32";
    private final VisualStudioTargetBinary binary;

    public VisualStudioProjectConfiguration(DefaultVisualStudioProject vsProject, String configurationName, VisualStudioTargetBinary binary) {
        this.vsProject = vsProject;
        this.configurationName = configurationName;
        this.name = configurationName + "|" + platformName;
        this.binary = binary;
    }

    @Input
    public String getName() {
        return name;
    }

    @Input
    public String getConfigurationName() {
        return configurationName;
    }

    @Input
    public String getPlatformName() {
        return platformName;
    }

    @Nested
    public VisualStudioTargetBinary getTargetBinary() {
        return binary;
    }

    @Internal
    public final String getType() {
        return "Makefile";
    }

    @Internal
    public DefaultVisualStudioProject getProject() {
        return vsProject;
    }

    @Internal
    public PublishArtifact getPublishArtifact() {
        return new VisualStudioProjectConfigurationArtifact();
    }

    @Input
    public String getBinaryOutputPath() {
        return binary.getOutputFile().getAbsolutePath();
    }

    private class VisualStudioProjectConfigurationArtifact extends DefaultPublishArtifact {
        public VisualStudioProjectConfigurationArtifact() {
            super(name, "vcxproj", ARTIFACT_TYPE, null, null, null, vsProject.getBuildDependencies());
        }

        @Override
        public File getFile() {
            return vsProject.getProjectFile().getLocation();
        }
    }
}
