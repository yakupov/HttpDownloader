package org.iyakupov.downloader.gui.main_window;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.core.file.IDownloadableFile;
import org.iyakupov.downloader.gui.new_download.DownloadRequest;
import org.iyakupov.downloader.gui.new_download.NewDownloadController;
import org.iyakupov.downloader.gui.settings.ISettingsModel;
import org.iyakupov.downloader.gui.settings.SettingsController;
import org.iyakupov.downloader.gui.settings.SettingsModel;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller of the main window (with downloadable file list etc)
 */
public class MainController implements Initializable, Closeable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SettingsModel settingsModel = new SettingsModel();
    private final IDispatchingQueue dispatcher = new DispatchingQueue(SettingsModel.DEFAULT_WORKER_THREADS_COUNT_PER_FILE);
    private final ObservableList<IDownloadableFile> downloadableFilesList = FXCollections.observableArrayList();
    //private final Timer tableRefreshTimer = new Timer();
    private Timeline tableRefreshTimeline;

    //FXML items
    public TableView<IDownloadableFile> allTasksTableView;
    public MenuItem newDownloadMenuItem;
    public MenuItem settingsMenuItem;

    public void openFileSubmitDialog() {
        try {
            final FXMLLoader fxmlLoader = new FXMLLoader(NewDownloadController.class.getResource("newDownload.fxml"));
            final Parent newDownloadParent = fxmlLoader.load();
            fxmlLoader.<NewDownloadController>getController().setMainController(this); //inject main controller
            final Stage stage = new Stage();
            stage.setTitle("Create a new file download request");
            stage.setScene(new Scene(newDownloadParent));
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to open the new download request dialog", e);
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Application error");
            alert.setHeaderText("Failed to open the new download request dialog");
            alert.setContentText(e.getMessage() + "\nPlease see error details in application logs.");
            alert.show();
        }
    }

    public void openSettings() {
        try {
            final FXMLLoader fxmlLoader = new FXMLLoader(SettingsController.class.getResource("settings.fxml"));
            final Parent settingsParent = fxmlLoader.load();
            fxmlLoader.<SettingsController>getController().setSettingsModel(settingsModel); //inject main controller
            final Stage stage = new Stage();
            stage.setTitle("Settings");
            stage.setScene(new Scene(settingsParent));
            stage.show();
        } catch (Exception e) {
            logger.error("Failed to open the settings dialog", e);
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Application error");
            alert.setHeaderText("Failed to open the settings dialog");
            alert.setContentText(e.getMessage() + "\nPlease see error details in application logs.");
            alert.show();
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //TODO: try to do it declaratively
        newDownloadMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+N"));
        settingsMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+ALT+S"));

        //React on change of the thread pool size
        settingsModel.getTotalNumberOfThreadsProperty().addListener((observable, oldValue, newValue) -> {
            dispatcher.setThreadPoolSize(newValue.intValue(), false);
        });

        allTasksTableView.setItems(downloadableFilesList);

        tableRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            allTasksTableView.setItems(FXCollections.observableArrayList(dispatcher.getAllFiles())); //FIXME
        }));
        tableRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        tableRefreshTimeline.play();

        /*
        tableRefreshTimer.schedule(
                new TimerTask() {
                    @Override
                    public void run() {
                        //allTasksTableView.setItems(downloadableFilesList); //FIXME
                        allTasksTableView.setItems(FXCollections.observableArrayList(dispatcher.getAllFiles())); //FIXME
                    }
                }, 0, 1000);*/
    }

    @NotNull
    public ISettingsModel getSettingsModel() {
        return settingsModel;
    }

    public void submitDownloadRequest(@NotNull DownloadRequest request) {
        logger.trace("New download request submitted: " + request);

        final IDownloadableFile downloadableFile = dispatcher.submitFile(request.getUrl(), request.getOutputDir(), request.getNumberOfThreads());

        downloadableFilesList.add(downloadableFile);

        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        logger.trace(downloadableFile.getProgress() + "_" + downloadableFile.getStatus());
        logger.trace(downloadableFile.getDownloadableParts().size() + "");
        logger.trace(downloadableFile.getDownloadableParts().get(0).getProgress() + "");




        //allTasksTableView.setItems(downloadableFilesList);

        //TODO
    }


    @Override
    public void close() throws IOException {
        dispatcher.close();
        //tableRefreshTimer.cancel();
        tableRefreshTimeline.stop();
    }
}
