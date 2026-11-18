package com.quizai.admin.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Native JavaFX/FXML startup check. No backend, credentials or business fixtures are used. */
public final class JavaFxSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        String[] resources=new String[]{"/fxml/shell.fxml","/fxml/auth.fxml","/fxml/workspace.fxml"};
        Platform.startup(()->{Platform.setImplicitExit(false);show(0,resources,finished,failure);});
        if(!finished.await(30,TimeUnit.SECONDS)){System.err.println("JavaFX startup timed out");System.exit(2);}
        if(failure.get()!=null){failure.get().printStackTrace();System.exit(1);}
        System.out.println("JAVAFX_STARTUP_PASS (FXML/native render only; no backend/API/Gemini verification)");
        System.exit(0);
    }
    private static void show(int index,String[] resources,CountDownLatch finished,AtomicReference<Throwable> failure){
        if(index==resources.length){finished.countDown();Platform.exit();return;}
        Stage stage=new Stage();
        try{
            String resource=resources[index];
            FXMLLoader loader=new FXMLLoader(JavaFxSmoke.class.getResource(resource));
            Parent view=loader.load();BorderPane shell=new BorderPane(view);shell.getStyleClass().add("theme-light");Scene scene=new Scene(shell,1280,800);
            scene.getStylesheets().add(JavaFxSmoke.class.getResource("/css/theme.css").toExternalForm());
            stage.setTitle("QuizAI smoke: "+resource);stage.setScene(scene);stage.show();
            PauseTransition pulse=new PauseTransition(Duration.millis(150));
            pulse.setOnFinished(event->{
                try{
                    view.applyCss();view.layout();
                    if(!stage.isShowing())throw new IllegalStateException("Stage did not show: "+resource);
                    var snapshot=scene.snapshot(null);
                    if(snapshot.getWidth()<1||snapshot.getHeight()<1)throw new IllegalStateException("Empty render: "+resource);
                    System.out.println("NATIVE_JAVAFX_FXML_PASS "+resource+" controller="+(loader.getController()==null?"none":loader.getController().getClass().getSimpleName()));
                    stage.close();show(index+1,resources,finished,failure);
                }catch(Throwable error){stage.close();failure.set(error);finished.countDown();Platform.exit();}
            });pulse.play();
        }catch(Throwable error){stage.close();failure.set(error);finished.countDown();Platform.exit();}
    }
}