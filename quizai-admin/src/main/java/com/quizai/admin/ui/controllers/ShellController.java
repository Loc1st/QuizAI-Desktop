package com.quizai.admin.ui.controllers;

import com.quizai.admin.ui.navigation.Screen;
import com.quizai.admin.ui.navigation.ViewLoader;
import com.quizai.common.api.QuizApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public final class ShellController {
    @FXML private SidebarController sidebarController;
    @FXML private Label pageTitle;
    @FXML private Label sessionLabel;
    @FXML private Button logoutButton;
    @FXML private StackPane contentHost;
    private final QuizApiClient api=QuizApiClient.configured("TEACHER");
    private Object current;

    @FXML private void initialize(){sidebarController.setOnNavigate(this::navigate);showAuth();}
    private void showAuth(){
        closeView();
        try{
            ViewLoader.LoadedView view=ViewLoader.load("/fxml/auth.fxml");current=view.controller();
            ((AuthController)current).configure(api,()->navigate(Screen.OVERVIEW));contentHost.getChildren().setAll(view.root());
            pageTitle.setText("Đăng nhập / Đăng ký");sessionLabel.setText("Chưa đăng nhập");logoutButton.setVisible(false);logoutButton.setManaged(false);sidebarController.setSignedIn(false);
        }catch(IOException error){throw new IllegalStateException("Cannot load authentication",error);}
    }
    public void navigate(Screen screen){
        if(api.currentUser()==null){showAuth();return;}closeView();
        try{
            ViewLoader.LoadedView view=ViewLoader.load("/fxml/workspace.fxml");current=view.controller();
            ((TeacherWorkspaceController)current).configure(api,this::showAuth,screen,this::navigate);
            contentHost.getChildren().setAll(view.root());sidebarController.select(screen);sidebarController.setSignedIn(true);
            pageTitle.setText(screen.title());sessionLabel.setText(api.currentUser().email());logoutButton.setVisible(true);logoutButton.setManaged(true);
        }catch(IOException error){throw new IllegalStateException("Cannot load destination: "+screen,error);}
    }
    @FXML private void logout(){api.logout();showAuth();}
    private void closeView(){if(current instanceof AuthController auth)auth.deactivate();else if(current instanceof AsyncController controller)controller.close();current=null;}
}
