package org.iyakupov.downloader.gui.new_download;

import javafx.event.ActionEvent;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.iyakupov.downloader.gui.main_window.MainController;

import java.io.File;

/**
 * Created by iyakupov on 07.04.2016.
 * (c) OpenWay Service
 */
public class NewDownloadController {
    public TextField outDirTextField;
    public TextField urlTextField;

    private MainController mainController;

    public void directoryChooseButtonPressed(ActionEvent actionEvent) {
        final DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose the directory for output files");
        final File dir = chooser.showDialog(new Stage());
        //chooser.setInitialDirectory(); //TODO: save init and add default directories

        if (dir != null) {
            outDirTextField.setText(dir.toString());
        }
    }

    public void submitButtonPressed(ActionEvent actionEvent) {
        System.out.println("'Submit this download request' pressed");
        //TODO: impl

        //TODO: logger


        if (mainController != null)
            mainController.saySomething();
        else
            System.out.println("NO CONTROLLER!!!");

    }

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }
}
