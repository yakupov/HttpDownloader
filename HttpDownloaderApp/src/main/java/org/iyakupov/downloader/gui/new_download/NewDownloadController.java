package org.iyakupov.downloader.gui.new_download;

import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.iyakupov.downloader.gui.main_window.MainController;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller of the new download request window
 */
public class NewDownloadController implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private MainController mainController;

    //FXML
    public TextField outDirTextField;
    public TextField nThreadsTextField;
    public TextField urlTextField;

    public void directoryChooseButtonPressed() {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the directory for output files");
        final File dir = chooser.showDialog(new Stage());
        if (mainController.getSettingsModel().getDefaultOutputFolder() != null)
            chooser.setInitialDirectory(mainController.getSettingsModel().getDefaultOutputFolder());

        if (dir != null) {
            outDirTextField.setText(dir.toString());
        }
    }

    public void submitButtonPressed() {
        if (mainController != null) {
            try {
                final File outputDir = new File(outDirTextField.getText());
                final int nThreads = Integer.parseInt(nThreadsTextField.getText());
                mainController.submitDownloadRequest(new DownloadRequest(outputDir, urlTextField.getText(), nThreads));
            } catch (Exception e) {
                logger.error("Failed to submit the download request", e);
                final Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Input error");
                alert.setHeaderText("Failed to submit the download request");
                alert.setContentText(e.getMessage() + "\nPlease see error details in application logs.");
                alert.show();
            }
        } else {
            logger.error("Main controller is not defined for the new download controller");
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Application error");
            alert.setHeaderText("Main controller is not defined for the new download controller");
            alert.show();
        }

    }

    public void setMainController(@NotNull MainController mainController) {
        this.mainController = mainController;

        if (mainController.getSettingsModel().getDefaultOutputFolder() != null)
            outDirTextField.setText(mainController.getSettingsModel().getDefaultOutputFolder().toString());
        nThreadsTextField.setText(String.valueOf(mainController.getSettingsModel().getDefaultNumberOfThreadsPerFile()));
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        nThreadsTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                nThreadsTextField.setText(oldValue);
            }
        });
    }
}
