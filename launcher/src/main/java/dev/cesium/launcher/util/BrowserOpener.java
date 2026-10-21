package dev.cesium.launcher.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;

/** Opens a URL in the system browser, trying several mechanisms in turn. */
public final class BrowserOpener {
    private final Consumer<String> hostOpener;

    /** @param hostOpener typically HostServices::showDocument; may be null */
    public BrowserOpener(Consumer<String> hostOpener) {
        this.hostOpener = hostOpener;
    }

    /** Returns true when some mechanism accepted the URL. */
    public boolean open(String url) {
        if (hostOpener != null) {
            try {
                hostOpener.accept(url);
                return true;
            } catch (RuntimeException ignored) {
                // fall through to the next mechanism
            }
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
                return true;
            }
        } catch (IOException | RuntimeException ignored) {
            // fall through to the next mechanism
        } catch (Throwable t) {
            if (t instanceof Error && !(t instanceof java.awt.AWTError || t instanceof LinkageError)) {
                throw (Error) t;
            }
        }
        OsInfo os = OsInfo.current();
        String[] cmd;
        if (os.isWindows()) {
            cmd = new String[]{"rundll32", "url.dll,FileProtocolHandler", url};
        } else if (os.isMac()) {
            cmd = new String[]{"open", url};
        } else {
            cmd = new String[]{"xdg-open", url};
        }
        try {
            new ProcessBuilder(cmd).redirectErrorStream(true).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
