package dev.cesium.launcher;

import dev.cesium.launcher.game.LauncherService;
import dev.cesium.launcher.ui.LauncherWindow;
import dev.cesium.launcher.util.LauncherPaths;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.Objects;

/** JavaFX application: borderless transparent window, CSS theme, one {@link LauncherWindow}. */
public final class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);
        stage.setTitle("Cesium - 1.21.11");

        LauncherWindow window = new LauncherWindow(stage, new LauncherService(LauncherPaths.defaults()),
                getHostServices());

        Scene scene = new Scene(window.root(), LauncherWindow.WIDTH, LauncherWindow.HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(Objects.requireNonNull(
                getClass().getResource("/dev/cesium/launcher/cesium.css")).toExternalForm());

        stage.setScene(scene);
        stage.show();
        window.start();
    }
}
