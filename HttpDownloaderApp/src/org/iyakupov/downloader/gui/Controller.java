package org.iyakupov.downloader.gui;

import javafx.event.ActionEvent;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCombination;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;

public class Controller implements Initializable {

    public TableView<TestModel> allTasksTableView;
    public MenuItem newDownloadMenuItem;

    TestModel lastTestModel;

    public void openFileSubmitDialog(ActionEvent actionEvent) {
        //TODO
        System.out.println("OPEN SUBMIT DIALOG!");

        final FileChooser fc = new FileChooser();
        fc.setTitle("Choose me");
        final File f = fc.showOpenDialog(new Stage()); //pushMe.getScene().getWindow());

        if (f != null) {
            if (lastTestModel != null) {
                lastTestModel.setC1("LAST1");
                lastTestModel.setTestEnum(TestEnum.TWO);
                lastTestModel.setC3("XXX");
            }
            lastTestModel = new TestModel(f.toString(), null, "3123456");
            allTasksTableView.getItems().add(lastTestModel);
        }


        //TODO: logic; replace with custom dialog;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        newDownloadMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+N")); //TODO: try to do it declaratively
    }
}
