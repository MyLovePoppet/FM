import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class FMApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("FM");
        Scene scene = new Scene(FXMLLoader.load(FMApp.class.getResource("FM.fxml")));
        primaryStage.getIcons().add(new Image(FMApp.class.getResourceAsStream("radio.png")));
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
