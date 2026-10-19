package dev.cesium.launcher.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

public final class FileSecurity {
    private FileSecurity() {
    }

    /** Restricts the file to owner read/write where the file system supports POSIX permissions. */
    public static void restrictToOwner(Path file) {
        try {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(file,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            } else {
                java.io.File f = file.toFile();
                f.setReadable(false, false);
                f.setWritable(false, false);
                f.setReadable(true, true);
                f.setWritable(true, true);
            }
        } catch (IOException | UnsupportedOperationException | SecurityException ignored) {
            // Best effort: the file is still written, just without tightened permissions.
        }
    }
}
