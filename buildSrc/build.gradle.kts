/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.jvm.toolchain.internal.JavaInstallationProbe
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.util.Properties
import kotlin.coroutines.experimental.EmptyCoroutineContext.plus

plugins {
    `kotlin-dsl`
}

subprojects {
    if (file("src/main/groovy").isDirectory || file("src/test/groovy").isDirectory) {
        apply { plugin("groovy") }
        dependencies {
            compile(localGroovy())
            testCompile("org.spockframework:spock-core:1.0-groovy-2.4")
            testCompile("cglib:cglib-nodep:3.2.5")
            testCompile("org.objenesis:objenesis:2.4")
            constraints {
                compile("org.codehaus.groovy:groovy-all:2.4.12")
            }
        }

        fun configureCompileTask(task: AbstractCompile, options: CompileOptions) {
            options.isFork = true
            options.encoding = "utf-8"
            options.compilerArgs = mutableListOf("-Xlint:-options", "-Xlint:-path")
            val vendor = System.getProperty("java.vendor")
            task.inputs.property("javaInstallation", "${vendor} ${JavaVersion.current()}")
        }

        tasks.withType<GroovyCompile> {
            groovyOptions.encoding = "utf-8"
            configureCompileTask(this, options)
        }
    }
    if (file("src/main/kotlin").isDirectory || file("src/test/kotlin").isDirectory) {
        apply { plugin("kotlin") }
    }
    apply {
        plugin("idea")
        plugin("eclipse")
    }

    dependencies {
        compile(gradleApi())
    }
}

allprojects {
    repositories {
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://repo.gradle.org/gradle/libs-snapshots") }
        gradlePluginPortal()
    }
}

dependencies {
    subprojects.forEach {
        "runtime"(project(it.path))
    }
}

val isCiServer: Boolean by extra { System.getenv().containsKey("CI") }
if (!isCiServer || System.getProperty("enableCodeQuality")?.toLowerCase() == "true") {
    apply { from("../gradle/codeQualityConfiguration.gradle.kts") }
}

if (isCiServer) {
    gradle.buildFinished {
        tasks.all {
            if (this is Reporting<*> && state.failure != null) {
                prepareReportForCIPublishing(this.reports["html"].destination)
            }
        }
    }
}

fun Project.prepareReportForCIPublishing(report: File) {
    if (report.isDirectory) {
        val destFile = File("${rootProject.buildDir}/report-$name-${report.name}.zip")
        ant.withGroovyBuilder {
            "zip"("destFile" to destFile) {
                "fileset"("dir" to report)
            }
        }
    } else {
        copy {
            from(report)
            into(rootProject.buildDir)
            rename { "report-$name-${report.parentFile.name}-${report.name}" }
        }
    }
}

fun readProperties(propertiesFile: File) = Properties().apply {
    propertiesFile.inputStream().use { fis ->
        load(fis)
    }
}

tasks {
    val checkSameDaemonArgs by creating {
        doLast {
            val buildSrcProperties = readProperties(File(project.rootDir, "gradle.properties"))
            val rootProperties = readProperties(File(project.rootDir, "../gradle.properties"))
            val jvmArgs = listOf(buildSrcProperties, rootProperties).map { it.getProperty("org.gradle.jvmargs") }.toSet()
            if (jvmArgs.size > 1) {
                throw GradleException("gradle.properties and buildSrc/gradle.properties have different org.gradle.jvmargs " +
                    "which may cause two daemons to be spawned on CI and in IDEA. " +
                    "Use the same org.gradle.jvmargs for both builds.")
            }
        }
    }

    val build by getting
    build.dependsOn(checkSameDaemonArgs)
}
