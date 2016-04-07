package org.iyakupov.downloader.gui.main_window;

import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import org.iyakupov.downloader.core.dispatch.IDispatchingQueue;
import org.iyakupov.downloader.core.dispatch.impl.DispatchingQueue;
import org.iyakupov.downloader.gui.new_download.NewDownloadController;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class MainController implements Initializable {
    private static final int DEFAULT_WORKER_THREADS_COUNT = 10;

    private final IDispatchingQueue dispatcher = new DispatchingQueue(DEFAULT_WORKER_THREADS_COUNT);

    public TableView<TestModel> allTasksTableView;
    public MenuItem newDownloadMenuItem;

    TestModel lastTestModel;

    public void openFileSubmitDialog(ActionEvent actionEvent) {
        try {
            final FXMLLoader fxmlLoader = new FXMLLoader(NewDownloadController.class.getResource("newDownload.fxml"));
            final Parent newDownloadParent = fxmlLoader.load();
            fxmlLoader.<NewDownloadController>getController().setMainController(this); //inject main controller
            final Stage stage = new Stage();
            //stage.initModality(Modality.APPLICATION_MODAL);
            //stage.initStyle(StageStyle.UNDECORATED);
            stage.setTitle("Create a new file download request");
            stage.setScene(new Scene(newDownloadParent));
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            //TODO: process
        }

        //TODO
        System.out.println("OPEN SUBMIT DIALOG!");
/*
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
*/

        //TODO: logic; replace with custom dialog;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        newDownloadMenuItem.setAccelerator(KeyCombination.keyCombination("SHORTCUT+N")); //TODO: try to do it declaratively
    }

    public void saySomething() {
        System.out.println("Main controller is alive!");
    }
}
