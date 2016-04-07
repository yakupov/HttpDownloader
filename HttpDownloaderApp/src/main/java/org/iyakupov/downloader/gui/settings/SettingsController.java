package org.iyakupov.downloader.gui.settings;

import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller of the applications settings window
 */
public class SettingsController implements Initializable {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private SettingsModel settingsModel;

    //FXML
    public TextField outDirTextField;
    public TextField totalThreadsTextField;
    public TextField fileThreadsTextField;

    public void setSettingsModel(@NotNull SettingsModel settingsModel) {
        this.settingsModel = settingsModel;

        if (settingsModel.getDefaultOutputFolder() != null)
            outDirTextField.setText(settingsModel.getDefaultOutputFolder().toString());

        totalThreadsTextField.setText(String.valueOf(settingsModel.getTotalNumberOfThreads()));
        fileThreadsTextField.setText(String.valueOf(settingsModel.getDefaultNumberOfThreadsPerFile()));
    }

    public void chooseDefaultOutputFolderPressed() {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the directory for output files");
        final File dir = chooser.showDialog(new Stage());

        if (dir != null) {
            outDirTextField.setText(dir.toString());
        }

    }

    public void saveButtonPressed() {
        if (settingsModel == null) {
            logger.error("Data model is not defined fot settings controller, but save button was pressed");
            final Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Application error");
            alert.setHeaderText("Data model is not defined fot settings controller");
            alert.show();
        } else {
            if (fileThreadsTextField.getText() != null)
                settingsModel.setDefaultNumberOfThreadsPerFile(Integer.parseInt(fileThreadsTextField.getText()));

            if (totalThreadsTextField.getText() != null)
                settingsModel.setTotalNumberOfThreads(Integer.parseInt(totalThreadsTextField.getText()));

            if (outDirTextField.getText() != null) {
                try {
                    final File outputDir = new File(outDirTextField.getText());
                    if (!outputDir.exists()) {
                        logger.error("The chosen output directory does not exist");
                        final Alert alert = new Alert(Alert.AlertType.ERROR);
                        alert.setTitle("Input error");
                        alert.setHeaderText("The chosen output directory does not exist");
                        alert.show();
                    } else {
                        settingsModel.setDefaultOutputFolder(outputDir);
                    }
                } catch (Exception e) {
                    logger.error("Failed parse the output directory path", e);
                    final Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Input error");
                    alert.setHeaderText("Failed parse the output directory path");
                    alert.setContentText(e.getMessage() + "\nPlease see error details in application logs.");
                    alert.show();
                }
            }
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        totalThreadsTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                totalThreadsTextField.setText(oldValue);
            }
        });

        fileThreadsTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                fileThreadsTextField.setText(oldValue);
            }
        });
    }
}
