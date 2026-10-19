package dev.cesium.launcher;

import javafx.application.Application;

/** Plain entry point so the shaded jar runs without the JavaFX module path being set up by hand. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
