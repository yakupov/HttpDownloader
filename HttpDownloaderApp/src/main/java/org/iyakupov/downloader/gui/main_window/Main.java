package org.iyakupov.downloader.gui.main_window;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        final FXMLLoader fxmlLoader = new FXMLLoader();
        final Pane root = fxmlLoader.load(MainController.class.getResource("main.fxml").openStream());
        primaryStage.setTitle("Downloader");
        final Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(event -> {
            try {
                fxmlLoader.<MainController>getController().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            Platform.exit();
        });
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
