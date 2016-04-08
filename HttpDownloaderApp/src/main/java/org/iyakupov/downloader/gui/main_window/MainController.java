package org.iyakupov.downloader.gui.main_window;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.iyakupov.downloader.core.DownloadStatus;
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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Controller of the main window (with downloadable file list etc)
 */
public class MainController implements Initializable, Closeable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final SettingsModel settingsModel = new SettingsModel();
    private final IDispatchingQueue dispatcher = new DispatchingQueue(SettingsModel.DEFAULT_WORKER_THREADS_COUNT_PER_FILE);
    private Timeline tableRefreshTimeline;

    //FXML items
    public TableView<IDownloadableFile> allTasksTableView;
    public MenuItem newDownloadMenuItem;
    public MenuItem settingsMenuItem;
    public Label totalProgressCounterLabel;
    public TableColumn<IDownloadableFile, Double> completionPercentColumn;
    public TableColumn<IDownloadableFile, Integer> downloadSpeedColumn;

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
        newDownloadMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+N"));
        settingsMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+ALT+S"));

        //React on change of the thread pool size
        settingsModel.getTotalNumberOfThreadsProperty().addListener((observable, oldValue, newValue) -> {
            dispatcher.setThreadPoolSize(newValue.intValue(), false);
        });

        allTasksTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        final DecimalFormat preferredDoubleFormat = new DecimalFormat("##0.00");

        //Task state refresh. This is an ugly workaround, because it's not possible to make 'elements' of
        //IDownloadableFile Observable, and there is no 'refreshPeriod' in the TableView.
        tableRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            final List<IDownloadableFile> oldSelection = new ArrayList<>(allTasksTableView.getSelectionModel().getSelectedItems());
            allTasksTableView.getItems().clear();
            allTasksTableView.getItems().addAll(dispatcher.getAllFiles());
            oldSelection.forEach(s -> {
                if (dispatcher.getAllFiles().contains(s))
                    allTasksTableView.getSelectionModel().select(s);
            });
            final double totalProgress = dispatcher.getAllFiles().stream()
                    .mapToDouble(IDownloadableFile::getProgress)
                    .average().orElse(0);
            totalProgressCounterLabel.setText(preferredDoubleFormat.format(totalProgress * 100));
        }));
        tableRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        tableRefreshTimeline.play();

        //Format columns
        completionPercentColumn.setCellFactory(column -> new TableCell<IDownloadableFile, Double>() {
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(preferredDoubleFormat.format(item * 100));
                }
            }
        });

        downloadSpeedColumn.setCellFactory(column -> new TableCell<IDownloadableFile, Integer>() {
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(null);
                    setStyle("");
                } else {
                    if (item >= 1e6) {
                        setText(preferredDoubleFormat.format((double) item / 1e6) + " Mb/s");
                    } else if (item >= 1e3) {
                        setText(preferredDoubleFormat.format((double) item / 1e3) + " Kb/s");
                    } else {
                        setText(item + " b/s");
                    }
                }
            }
        });
    }

    @NotNull
    public ISettingsModel getSettingsModel() {
        return settingsModel;
    }

    public void submitDownloadRequest(@NotNull DownloadRequest request) {
        logger.trace("New download request submitted: " + request);
        try {
            dispatcher.submitFile(request.getUrl(), request.getOutputDir(), request.getNumberOfThreads());
        } catch (RuntimeException e) {
            logger.error("Failed to submit the new file download request", e);
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Runtime error");
            alert.setHeaderText("Failed to submit the new file download request");
            alert.setContentText(e.getMessage() + "\nPlease see error details in application logs.");
            alert.show();
        }
    }

    @Override
    public void close() throws IOException {
        dispatcher.close();
        if (tableRefreshTimeline != null)
            tableRefreshTimeline.stop();
    }

    public void pauseTasks() {
        final List<IDownloadableFile> selectedFiles = allTasksTableView.getSelectionModel().getSelectedItems();
        selectedFiles.forEach(IDownloadableFile::pause);
    }

    public void resumeTasks() {
        final List<IDownloadableFile> selectedFiles = allTasksTableView.getSelectionModel().getSelectedItems();
        selectedFiles.stream().filter(f -> f.getStatus() == DownloadStatus.PAUSED).forEach(dispatcher::resumeDownload);
    }

    public void cancelTasks() {
        final List<IDownloadableFile> selectedFiles = allTasksTableView.getSelectionModel().getSelectedItems();
        selectedFiles.forEach(f -> {
            f.cancel();
            dispatcher.forgetFile(f);
        });
    }

    public void closeApp() throws IOException {
        close();
        Platform.exit();
    }
}
