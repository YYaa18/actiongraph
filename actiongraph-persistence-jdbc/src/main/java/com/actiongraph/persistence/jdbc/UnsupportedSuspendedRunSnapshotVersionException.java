package com.actiongraph.persistence.jdbc;

import com.actiongraph.exception.ActionGraphConfigurationException;

/**
 * Raised when a persisted suspended-run snapshot was written with an unsupported
 * format version.
 */
public final class UnsupportedSuspendedRunSnapshotVersionException extends ActionGraphConfigurationException {
    private static final long serialVersionUID = 1L;

    private final int snapshotVersion;
    private final int supportedVersion;

    public UnsupportedSuspendedRunSnapshotVersionException(int snapshotVersion, int supportedVersion) {
        super("Unsupported suspended run snapshot version " + snapshotVersion
                + "; supported version is " + supportedVersion);
        this.snapshotVersion = snapshotVersion;
        this.supportedVersion = supportedVersion;
    }

    public int snapshotVersion() {
        return snapshotVersion;
    }

    public int supportedVersion() {
        return supportedVersion;
    }
}
