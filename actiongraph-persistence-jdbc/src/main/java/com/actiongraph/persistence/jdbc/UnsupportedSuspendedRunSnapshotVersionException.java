package com.actiongraph.persistence.jdbc;

public final class UnsupportedSuspendedRunSnapshotVersionException extends IllegalStateException {
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
