package com.actiongraph.console.studio.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "actiongraph.studio")
@Experimental(
        since = "0.2.0",
        value = "Goal Studio is experimental and intended for non-production drafting environments."
)
public class ActionGraphStudioProperties implements SharedSecretTokenProperties {
    private boolean enabled = false;
    private String path = "/actiongraph/studio";
    private String tokenHeader = "X-ActionGraph-Studio-Token";
    private String sharedSecret = "";
    private List<String> forbiddenProfiles = new ArrayList<>(List.of("prod", "production"));
    private int maxAutoRepairs = 3;
    private Path bundleDirectory = Path.of("build/actiongraph-studio-bundles");
    private String sourceEnv = "test";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("studio path must not be blank");
        }
        this.path = path;
    }

    @Override
    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("studio token header must not be blank");
        }
        this.tokenHeader = tokenHeader;
    }

    @Override
    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        if (sharedSecret != null && sharedSecret.isBlank()) {
            throw new IllegalArgumentException("studio shared secret must not be blank");
        }
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public List<String> getForbiddenProfiles() {
        return forbiddenProfiles;
    }

    public void setForbiddenProfiles(List<String> forbiddenProfiles) {
        this.forbiddenProfiles = forbiddenProfiles == null
                ? new ArrayList<>()
                : new ArrayList<>(forbiddenProfiles);
    }

    public int getMaxAutoRepairs() {
        return maxAutoRepairs;
    }

    public void setMaxAutoRepairs(int maxAutoRepairs) {
        if (maxAutoRepairs < 0) {
            throw new IllegalArgumentException("studio max-auto-repairs must not be negative");
        }
        this.maxAutoRepairs = maxAutoRepairs;
    }

    public Path getBundleDirectory() {
        return bundleDirectory;
    }

    public void setBundleDirectory(Path bundleDirectory) {
        this.bundleDirectory = bundleDirectory == null
                ? Path.of("build/actiongraph-studio-bundles")
                : bundleDirectory;
    }

    public String getSourceEnv() {
        return sourceEnv;
    }

    public void setSourceEnv(String sourceEnv) {
        this.sourceEnv = sourceEnv == null || sourceEnv.isBlank() ? "test" : sourceEnv;
    }
}
