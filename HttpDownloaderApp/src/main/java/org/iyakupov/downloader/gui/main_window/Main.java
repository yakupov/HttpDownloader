package org.iyakupov.downloader.gui.main_window;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;

import java.io.IOException;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        final FXMLLoader fxmlLoader = new FXMLLoader();
        final Pane root = fxmlLoader.load(MainController.class.getResource("main.fxml").openStream());
        primaryStage.setTitle("Downloader");
        final Scene scene = new Scene(root); //TODO: default size needed?
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
