package org.jetbrains.intellij.tasks

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.jbr.JbrResolver

class RunPluginVerifierTask extends ConventionTask {
    private static final String BINTRAY_API_VERIFIER_VERSION_LATEST = "https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest"
    public static final String VERIFIER_VERSION_LATEST = "latest"

    static enum FailureLevel {
        COMPATIBILITY_WARNINGS("Compatibility warnings"),
        COMPATIBILITY_PROBLEMS("Compatibility problems"),
        DEPRECATED_API_USAGES("Deprecated API usages"),
        EXPERIMENTAL_API_USAGES("Experimental API usages"),
        INTERNAL_API_USAGES("Internal API usages"),
        OVERRIDE_ONLY_API_USAGES("Override-only API usages"),
        NON_EXTENDABLE_API_USAGES("Non-extendable API usages"),
        PLUGIN_STRUCTURE_WARNINGS("Plugin structure warnings"),
        MISSING_DEPENDENCIES("Missing dependencies"),
        INVALID_PLUGIN("The following files specified for the verification are not valid plugins"),
        NOT_DYNAMIC("Plugin cannot be loaded/unloaded without IDE restart");

        public static final EnumSet<FailureLevel> ALL = EnumSet.allOf(FailureLevel.class)
        public static final EnumSet<FailureLevel> NONE = EnumSet.noneOf(FailureLevel.class)

        public final String testValue

        FailureLevel(String testValue) {
            this.testValue = testValue
        }
    }

    private EnumSet<FailureLevel> failureLevel
    private List<Object> ideVersions = []
    private Object verifierVersion
    private Object distributionFile
    private Object verificationReportsDir
    private Object jbrVersion
    private Object runtimeDir
    private Object externalPrefixes
    private Object teamCity
    private Object subsystemsToCheck

    /**
     * Returns a list of the {@link FailureLevel} values used for failing the task if any reported issue will match.
     * @return
     */
    @Input
    EnumSet<FailureLevel> getFailureLevel() {
        return failureLevel
    }

    /**
     * Sets a list of the {@link FailureLevel} values that will make the task if any reported issue will match.
     *
     * @param failureLevel EnumSet of {@link FailureLevel} values
     */
    void setFailureLevel(EnumSet<FailureLevel> failureLevel) {
        this.failureLevel = failureLevel
    }

    /**
     * Sets the {@link FailureLevel} value that will make the task if any reported issue will match.
     *
     * @param failureLevel {@link FailureLevel} value
     */
    void setFailureLevel(FailureLevel failureLevel) {
        this.failureLevel = EnumSet.of(failureLevel)
    }

    /**
     * Returns a list of the specified IDE versions used for the verification.
     * By default, uses the plugin target IDE version.
     *
     * @return IDE versions list
     */
    @SkipWhenEmpty
    @Input
    List<String> getIdeVersions() {
        return CollectionUtils.stringize(ideVersions.collect {
            it instanceof Closure ? (it as Closure).call() : it
        }.flatten())
    }

    /**
     * Sets a list of the IDE versions used for the verification. Accepts list of {@link String} or {@link Closure}.
     *
     * @param ideVersions list of IDE versions
     */
    void setIdeVersions(List<Object> ideVersions) {
        this.ideVersions = ideVersions
    }

    /**
     * Sets a list of the IDE versions used for the verification.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param ideVersions list of IDE versions
     */
    void ideVersions(List<Object> ideVersions) {
        this.ideVersions = ideVersions
    }

    /**
     * Returns the version of the IntelliJ Plugin Verifier that will be used.
     * By default, set to "latest".
     *
     * @return verifierVersion IntelliJ Plugin Verifier version
     */
    @Input
    @Optional
    String getVerifierVersion() {
        return Utils.stringInput(verifierVersion)
    }

    /**
     * Sets the version of the IntelliJ Plugin Verifier that will be used.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verifierVersion IntelliJ Plugin Verifier version
     */
    void setVerifierVersion(Object verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    /**
     * Sets the version of the IntelliJ Plugin Verifier that will be used.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verifierVersion IntelliJ Plugin Verifier version
     */
    void verifierVersion(Object verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    /**
     * Returns an instance of the distribution file generated with the build task.
     * If empty, task will be skipped.
     *
     * @return generated plugin artifact
     */
    @SkipWhenEmpty
    @InputFile
    File getDistributionFile() {
        def input = distributionFile instanceof Closure ? (distributionFile as Closure).call() : distributionFile
        return input != null ? project.file(input) : null
    }

    /**
     * Sets an instance of the distribution file generated with the build task.
     * Accepts {@link File} or {@link Closure}.
     *
     * @param distributionFile generated plugin artifact
     */
    void setDistributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    /**
     * Sets an instance of the distribution file generated with the build task.
     * Accepts {@link File} or {@link Closure}.
     *
     * @param distributionFile generated plugin artifact
     */
    void distributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    /**
     * Returns the path to directory where verification reports will be saved.
     * By default, set to ${project.buildDir}/reports/pluginsVerifier.
     *
     * @return path to verification reports directory
     */
    @Input
    @Optional
    String getVerificationReportsDir() {
        return Utils.stringInput(verificationReportsDir)
    }

    /**
     * Sets the path to directory where verification reports will be saved.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verificationReportsDir path to verification reports directory
     */
    void setVerificationReportsDir(Object verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    /**
     * Sets the path to directory where verification reports will be saved.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verificationReportsDir path to verification reports directory
     */
    void verificationReportsDir(Object verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    @Input
    @Optional
    String getJbrVersion() {
        return Utils.stringInput(jbrVersion)
    }

    void setJbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    void jbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    @Input
    @Optional
    String getRuntimeDir() {
        return Utils.stringInput(runtimeDir)
    }

    void setRuntimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    void runtimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    @Input
    @Optional
    String getExternalPrefixes() {
        return Utils.stringInput(externalPrefixes)
    }

    void setExternalPrefixes(Object externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    void externalPrefixes(Object externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    @Input
    @Optional
    String getTeamCity() {
        return Utils.stringInput(teamCity)
    }

    void setTeamCity(Object teamCity) {
        this.teamCity = teamCity
    }

    void teamCity(Object teamCity) {
        this.teamCity = teamCity
    }

    @Input
    @Optional
    String getSubsystemsToCheck() {
        return Utils.stringInput(subsystemsToCheck)
    }

    void setSubsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    void subsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    void runPluginVerifier() {
        def file = getDistributionFile()
        if (file == null || !file.exists()) {
            throw new IllegalStateException("Plugin file does not exist: $file")
        }

        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def resolver = new IdeaDependencyManager(extension.intellijRepo ?: IntelliJPlugin.DEFAULT_INTELLIJ_REPO)
        def verifierPath = getVerifierPath()

        def verifierArgs = ["check-plugin"]
        verifierArgs += getOptions()
        verifierArgs += [file.absolutePath]
        verifierArgs += getIdeVersions().collect {
            def (String type, String version) = it.split("-")
            def dependency = resolver.resolveRemote(project, version, type, false)
            return dependency.classes.absolutePath
        }

        new ByteArrayOutputStream().withStream { os ->
            project.javaexec {
                classpath = project.files(verifierPath)
                main = "com.jetbrains.pluginverifier.PluginVerifierMain"
                args = verifierArgs
                standardOutput = os
            }

            def output = os.toString()
            println output
            for (FailureLevel level : FailureLevel.values()) {
                if (failureLevel.contains(level) && output.contains(level.testValue)) {
                    throw new GradleException(level.toString())
                }
            }
        }
    }

    /**
     * Fetches IntelliJ Plugin Verifier artifact from the {@link IntelliJPlugin#DEFAULT_INTELLIJ_PLUGIN_SERVICE}
     * repository and resolves the path to verifier-cli jar file.
     *
     * @return path to verifier-cli jar
     */
    private String getVerifierPath() {
        def repository = project.repositories.maven { it.url = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGIN_SERVICE }
        try {
            def resolvedVerifierVersion = resolveVerifierVersion()
            def dependency = project.dependencies.create("org.jetbrains.intellij.plugins:verifier-cli:$resolvedVerifierVersion:all@jar")
            def configuration = project.configurations.detachedConfiguration(dependency)
            return configuration.singleFile.absolutePath
        }
        finally {
            project.repositories.remove(repository)
        }
    }

    /**
     * Resolves Plugin Verifier version.
     * If set to {@link #VERIFIER_VERSION_LATEST}, there's request to {@link #BINTRAY_API_VERIFIER_VERSION_LATEST}
     * performed for the latest available verifier version.
     *
     * @return Plugin Verifier version
     */
    private String resolveVerifierVersion() {
        if (getVerifierVersion() != VERIFIER_VERSION_LATEST) {
            return getVerifierVersion()
        }

        def url = new URL(BINTRAY_API_VERIFIER_VERSION_LATEST)
        return new JsonSlurper().parse(url)["name"]
    }

    /**
     * Resolves the Java Runtime directory. `runtimeDir` property is used if provided with the task configuration.
     * Otherwise, `jbrVersion` is used for resolving the JBR. If it's not set, or it's impossible to resolve valid
     * version, built-in JBR will be used.
     * As a last fallback, current JVM will be used.
     *
     * @return path to the Java Runtime directory
     */
    private String resolveRuntimeDir() {
        if (runtimeDir != null) {
            return getRuntimeDir()
        }

        def jbrResolver = new JbrResolver(project, this)
        if (jbrVersion != null) {
            def jbr = jbrResolver.resolve(getJbrVersion())
            if (jbr != null) {
                return jbr.javaHome
            }
            Utils.warn(this, "Cannot resolve JBR ${getJbrVersion()}. Falling back to builtin JBR.")
        }

        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def jbrPath = OperatingSystem.current().isMacOsX() ? "jbr/Contents/Home" : "jbr"

        def builtinJbrVersion = Utils.getBuiltinJbrVersion(Utils.ideSdkDirectory(project, extension))
        if (builtinJbrVersion != null) {
            def builtinJbr = jbrResolver.resolve(builtinJbrVersion)
            if (builtinJbr != null) {
                def javaHome = new File(builtinJbr.javaHome, jbrPath)
                if (javaHome.exists()) {
                    return javaHome
                }
            }
            Utils.warn(this, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
        }

        if (extension.alternativeIdePath) {
            def javaHome = new File(Utils.ideaDir(extension.alternativeIdePath), jbrPath)
            if (javaHome.exists()) {
                return javaHome
            }
            Utils.warn(this, "Cannot resolve JBR at $javaHome. Falling back to current JVM.")
        }

        return Jvm.current().getJavaHome()
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with available CLI options
     */
    private List<String> getOptions() {
        def args = [
                "-verification-reports-dir", getVerificationReportsDir(),
                "-runtime-dir", resolveRuntimeDir()
        ]

        if (externalPrefixes != null) {
            args += ["-external-prefixes", getExternalPrefixes()]
        }
        if (teamCity != null) {
            args += ["-team-city", getTeamCity()]
        }
        if (subsystemsToCheck != null) {
            args += ["-subsystems-to-check", getSubsystemsToCheck()]
        }

        return args
    }
}
