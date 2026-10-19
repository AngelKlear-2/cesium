package dev.cesium.launcher.ui;

import dev.cesium.launcher.auth.Account;
import dev.cesium.launcher.auth.AccountType;
import dev.cesium.launcher.game.LauncherService;
import dev.cesium.launcher.game.VersionService;
import dev.cesium.launcher.util.BrowserOpener;
import dev.cesium.launcher.util.LauncherException;
import dev.cesium.launcher.util.OsInfo;
import javafx.animation.FadeTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * The whole launcher UI: borderless panel with a custom title bar, a login view (Ely.by / offline)
 * and the main view (version, RAM, Java, PLAY). Views cross-fade; all work runs off the FX thread.
 */
public final class LauncherWindow {
    public static final int WIDTH = 640;
    public static final int HEIGHT = 480;

    /** The Cesium Fabric build is a synthetic entry; its id never reaches the manifest. */
    static final VersionService.Entry CESIUM = new VersionService.Entry("cesium-1.21.11", "cesium", null);

    private final Stage stage;
    private final LauncherService service;
    private final BrowserOpener browser;

    private final StackPane root = new StackPane();
    private final VBox loginView = new VBox(16);
    private final VBox mainView = new VBox(14);

    private final ToggleGroup tabs = new ToggleGroup();
    private final ToggleButton elyTab = new ToggleButton("ELY.BY");
    private final ToggleButton offlineTab = new ToggleButton("OFFLINE");
    private final TextField nickname = new TextField();
    private final Button offlineEnter = new Button("ENTER");
    private final Button elyLogin = new Button("LOGIN WITH ELY.BY");
    private final VBox offlineBox = new VBox(10);
    private final VBox elyBox = new VBox(10);

    private final Label accountChip = new Label();
    private final Button logout = new Button("LOG OUT");
    private final ComboBox<VersionService.Entry> versions = new ComboBox<>();
    private final Slider ram = new Slider(1, 16, 4);
    private final Label ramLabel = new Label();
    private final TextField javaPath = new TextField(OsInfo.current().defaultJavaPath());
    private final Label gameDirLabel = new Label();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label progressText = new Label();
    private final Button play = new Button("PLAY");

    private final Label status = new Label();

    private Account current;
    private Process game;
    private double dragX;
    private double dragY;
    private boolean busy;

    public LauncherWindow(Stage stage, LauncherService service, HostServices hosts) {
        this.stage = stage;
        this.service = service;
        this.browser = new BrowserOpener(hosts::showDocument);
        build();
    }

    public StackPane root() {
        return root;
    }

    /** Loads saved accounts and the version manifest. Call once the scene is shown. */
    public void start() {
        fade(root, 0, 1);
        runAsync(() -> {
            service.accounts().load();
            Optional<Account> last = service.accounts().lastUsed();
            return last.isPresent() ? service.refreshIfNeeded(last.get()) : null;
        }, a -> {
            if (a != null) {
                setAccount(a);
            } else {
                showView(loginView, false);
            }
        }, e -> {
            showView(loginView, false);
            say("! " + e.getMessage());
        });

        runAsync(() -> service.versions().releases(), list -> versions.getItems().addAll(list),
                e -> say("! Could not load the version list: " + e.getMessage()));
    }

    // ---------------------------------------------------------------- layout

    private void build() {
        root.getStyleClass().add("panel");
        root.setPrefSize(WIDTH, HEIGHT);
        root.setPadding(new Insets(0));

        VBox body = new VBox(0, titleBar(), contentArea());
        body.setPadding(new Insets(0));
        root.getChildren().add(body);

        buildLogin();
        buildMain();
        loginView.setVisible(true);
        mainView.setVisible(false);
        mainView.setOpacity(0);

        root.setOpacity(0);
    }

    private Node titleBar() {
        Label title = new Label(TITLE);
        title.getStyleClass().add("title-text");

        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);

        Button min = new Button("–");
        min.getStyleClass().add("window-button");
        min.setOnAction(e -> stage.setIconified(true));

        Button close = new Button("✕");
        close.getStyleClass().add("window-button");
        close.setOnAction(e -> stage.close());

        HBox bar = new HBox(8, title, spacer, min, close);
        bar.getStyleClass().add("title-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setOnMousePressed(e -> {
            dragX = e.getSceneX();
            dragY = e.getSceneY();
        });
        bar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - dragX);
            stage.setY(e.getScreenY() - dragY);
        });
        return bar;
    }

    private Node contentArea() {
        StackPane area = new StackPane(loginView, mainView);
        area.setPadding(new Insets(12, 36, 28, 36));
        VBox.setVgrow(area, javafx.scene.layout.Priority.ALWAYS);
        return area;
    }

    private void buildLogin() {
        Label hero = new Label(TITLE);
        hero.getStyleClass().add("hero-text");
        Label hint = new Label("Pick how you want to play.");
        hint.getStyleClass().add("hint-text");

        elyTab.setToggleGroup(tabs);
        offlineTab.setToggleGroup(tabs);
        elyTab.setSelected(true);
        HBox tabRow = new HBox(10, elyTab, offlineTab);
        tabRow.setAlignment(Pos.CENTER);

        tabs.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
                return;
            }
            elyBox.setVisible(b == elyTab);
            elyBox.setManaged(b == elyTab);
            offlineBox.setVisible(b == offlineTab);
            offlineBox.setManaged(b == offlineTab);
        });

        nickname.setPromptText("NICKNAME (3-16 chars)");
        nickname.setOnAction(e -> submitOffline());
        offlineEnter.setOnAction(e -> submitOffline());
        offlineEnter.setMaxWidth(Double.MAX_VALUE);
        offlineBox.getChildren().addAll(nickname, offlineEnter);
        offlineBox.setVisible(false);
        offlineBox.setManaged(false);

        elyLogin.setMaxWidth(Double.MAX_VALUE);
        elyLogin.getStyleClass().add("play-button");
        elyLogin.setStyle("-fx-font-size: 18px; -fx-padding: 10 20 10 20;");
        elyLogin.setOnAction(e -> loginEly());
        Label elyHint = new Label("Opens your browser. Ely.by keys go in ~/.cesium/launcher.properties.");
        elyHint.getStyleClass().add("hint-text");
        elyHint.setWrapText(true);
        elyBox.getChildren().addAll(elyLogin, elyHint);

        status.getStyleClass().add("status-text");
        status.setWrapText(true);

        loginView.getChildren().addAll(hero, hint, tabRow, elyBox, offlineBox, status);
        loginView.setAlignment(Pos.TOP_CENTER);
        loginView.setPadding(new Insets(24, 0, 0, 0));
    }

    private void buildMain() {
        Label heading = new Label(TITLE);
        heading.getStyleClass().add("hero-text");
        heading.setStyle("-fx-font-size: 40px;");

        accountChip.getStyleClass().add("chip");
        logout.setOnAction(e -> logOut());
        HBox accountRow = new HBox(10, accountChip, logout);
        accountRow.setAlignment(Pos.CENTER_LEFT);

        Label verLabel = section("VERSION");
        versions.setConverter(new StringConverter<>() {
            @Override
            public String toString(VersionService.Entry e) {
                if (e == null) {
                    return "";
                }
                return e == CESIUM || "cesium".equals(e.type()) ? "Cesium 1.21.11 (Fabric)" : e.id();
            }

            @Override
            public VersionService.Entry fromString(String s) {
                return null;
            }
        });
        versions.setMaxWidth(Double.MAX_VALUE);
        versions.getItems().add(CESIUM);
        versions.getSelectionModel().selectFirst();

        ram.setShowTickMarks(false);
        ram.setMajorTickUnit(1);
        ram.setSnapToTicks(true);
        ram.setPrefWidth(260);
        ramLabel.getStyleClass().add("hint-text");
        ram.valueProperty().addListener((o, a, b) -> ramLabel.setText(b.intValue() + " GB"));
        ramLabel.setText(ram.valueProperty().intValue() + " GB");
        HBox ramRow = new HBox(12, section("RAM"), ram, ramLabel);
        ramRow.setAlignment(Pos.CENTER_LEFT);

        javaPath.setPromptText("path to java");
        HBox javaRow = new HBox(12, section("JAVA"), javaPath);
        HBox.setHgrow(javaPath, javafx.scene.layout.Priority.ALWAYS);
        javaRow.setAlignment(Pos.CENTER_LEFT);

        gameDirLabel.getStyleClass().add("hint-text");
        gameDirLabel.setText("Game folder: ~/.cesium/game");

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setPrefHeight(10);
        progressText.getStyleClass().add("status-text");
        progressText.setWrapText(true);

        play.getStyleClass().add("play-button");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> startGame());

        mainView.getChildren().addAll(heading, accountRow, verLabel, versions, ramRow, javaRow,
                gameDirLabel, progress, progressText, play, status);
        mainView.setAlignment(Pos.TOP_CENTER);
        mainView.setPadding(new Insets(8, 0, 0, 0));
    }

    private static Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        l.setMinWidth(60);
        return l;
    }

    // ---------------------------------------------------------------- actions

    private void submitOffline() {
        String name = nickname.getText() == null ? "" : nickname.getText().strip();
        runAsync(() -> service.offline(name), this::setAccount,
                e -> say("! " + e.getMessage()));
    }

    private void loginEly() {
        say("Waiting for Ely.by in your browser…");
        runAsync(() -> service.elyLogin(url -> Platform.runLater(() -> browser.open(url))),
                this::setAccount,
                e -> say("! " + e.getMessage()));
    }

    private void logOut() {
        if (current == null) {
            return;
        }
        current = null;
        runAsync(() -> {
            service.accounts().clearSelection();
            return null;
        }, x -> showView(loginView, true), e -> say("! " + e.getMessage()));
    }

    private void setAccount(Account a) {
        current = a;
        accountChip.setText((a.type() == AccountType.ELYBY ? "ELY.BY" : "OFFLINE") + "  ·  " + a.username());
        showView(mainView, true);
        say("");
    }

    private void startGame() {
        if (current == null || busy) {
            return;
        }
        if (game != null && game.isAlive()) {
            say("Game is already running.");
            return;
        }
        VersionService.Entry chosen = versions.getValue();
        if (chosen == null) {
            say("! Pick a version first.");
            return;
        }
        boolean cesium = "cesium".equals(chosen.type());
        int gb = (int) Math.round(ram.getValue());
        String java = javaPath.getText().strip();
        Account acc = current;

        Consumer<String> logLine = System.out::println;
        setBusy(true);
        runAsync(() -> {
            Account fresh = service.refreshIfNeeded(acc);
            return service.play(fresh, chosen, cesium, gb, java,
                    (f, s) -> Platform.runLater(() -> {
                        progress.setProgress(f);
                        progressText.setText(s);
                    }), logLine);
        }, p -> {
            game = p;
            setBusy(false);
            progress.setProgress(1);
            progressText.setText("Game running");
            watch(p);
        }, e -> {
            setBusy(false);
            progress.setProgress(0);
            progressText.setText("");
            say("! " + e.getMessage());
        });
    }

    private void watch(Process p) {
        Thread t = new Thread(() -> {
            try {
                int code = p.waitFor();
                Platform.runLater(() -> {
                    progressText.setText("Game closed (exit " + code + ")");
                    progress.setProgress(0);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "cesium-game-watch");
        t.setDaemon(true);
        t.start();
    }

    // ---------------------------------------------------------------- plumbing

    private void setBusy(boolean b) {
        busy = b;
        play.setDisable(b);
        elyLogin.setDisable(b);
        offlineEnter.setDisable(b);
        logout.setDisable(b);
    }

    private void say(String text) {
        status.setText(text);
        if (text != null && !text.isBlank()) {
            fade(status, 0.4, 1);
        }
    }

    private void showView(VBox view, boolean animate) {
        Node from = view == loginView ? mainView : loginView;
        view.setVisible(true);
        if (!animate) {
            from.setVisible(false);
            from.setOpacity(0);
            view.setOpacity(1);
            return;
        }
        fade(from, 1, 0, () -> from.setVisible(false));
        fade(view, 0, 1);
    }

    private static void fade(Node n, double from, double to) {
        fade(n, from, to, null);
    }

    private static void fade(Node n, double from, double to, Runnable done) {
        FadeTransition ft = new FadeTransition(Duration.millis(260), n);
        ft.setFromValue(from);
        ft.setToValue(to);
        if (done != null) {
            ft.setOnFinished(e -> done.run());
        }
        ft.play();
    }

    private interface Work<T> {
        T run() throws Exception;
    }

    private <T> void runAsync(Work<T> work, Consumer<T> ok, Consumer<LauncherException> fail) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.run();
            }
        };
        task.setOnSucceeded(e -> ok.accept(task.getValue()));
        task.setOnFailed(e -> {
            Throwable t = task.getException();
            fail.accept(t instanceof LauncherException le ? le
                    : new LauncherException(t == null ? "Unknown error." : String.valueOf(t.getMessage()), t));
        });
        Thread th = new Thread(task, "cesium-worker");
        th.setDaemon(true);
        th.start();
    }

    private static final String TITLE = "Cesium - 1.21.11";
}
