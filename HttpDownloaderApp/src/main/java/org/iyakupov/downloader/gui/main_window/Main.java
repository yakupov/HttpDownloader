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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        final Parent root = FXMLLoader.load(getClass().getResource("main.fxml"));
        primaryStage.setTitle("Downloader");
        final Scene scene0 = new Scene(root); //TODO: default size needed?
        primaryStage.setScene(scene0);
        primaryStage.setOnCloseRequest(event -> Platform.exit());
        primaryStage.show();

        //Test stage/scene switch

        //1
        final StackPane stackPane1 = new StackPane();
        final Scene scene1 = new Scene(stackPane1, 300, 300);
        final Button button = new Button("xxx");
        stackPane1.getChildren().add(button);

        final Stage testStage = new Stage();
        testStage.setScene(scene1);
        //testStage.show();

        //2
        final StackPane stackPane2 = new StackPane();
        final Scene scene2 = new Scene(stackPane2, 800, 600);
        stackPane2.getChildren().add(new Button("SC2"));

        button.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                if (primaryStage.getScene() == scene2) {
                    primaryStage.setScene(scene0);
                    System.out.println("ok");
                } else {
                    primaryStage.setScene(scene2);
                    System.out.println("ok");
                }
                event.consume();
            }
        });
    }


    public static void main(String[] args) {
        IDispatchingQueue q = new DispatchingQueue(10);
        ObservableList<IDownloadableFile> l = FXCollections.observableArrayList(q.getAllFiles());
//TODO: cleanup


        launch(args);
    }
}
