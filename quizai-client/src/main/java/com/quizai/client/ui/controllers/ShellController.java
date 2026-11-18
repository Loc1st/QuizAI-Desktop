package com.quizai.client.ui.controllers;

import com.quizai.client.ui.navigation.Screen;
import com.quizai.client.ui.navigation.ViewLoader;
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
    private final QuizApiClient api=QuizApiClient.configured("STUDENT");
    private Object current;
    private boolean assignedExamActive;

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
        if(api.currentUser()==null){showAuth();return;}
        if(current instanceof ExamController exam&&!exam.confirmDiscard())return;
        if(assignedExamActive&&(screen==Screen.TUTOR||screen==Screen.PRACTICE)){
            Alert alert=new Alert(Alert.AlertType.INFORMATION,"Hỗ trợ AI không khả dụng trong khi có lượt kiểm tra đang làm. Quay lại Bài kiểm tra để hoàn tất hoặc đọc lại trạng thái.");alert.initOwner(contentHost.getScene().getWindow());alert.showAndWait();return;
        }
        closeView();
        try{
            ViewLoader.LoadedView view;
            if(screen==Screen.CLASSES){view=ViewLoader.load("/fxml/classes.fxml");((ClassesController)view.controller()).configure(api,this::showAuth);}
            else if(screen==Screen.EXAMS||screen==Screen.RESULTS||screen==Screen.OVERVIEW){
                view=ViewLoader.load("/fxml/exams.fxml");((ExamController)view.controller()).configure(api,this::showAuth,value->assignedExamActive=value);
            }else{
                view=ViewLoader.load("/fxml/study.fxml");((StudyController)view.controller()).configure(api,this::showAuth,screen);
            }
            current=view.controller();contentHost.getChildren().setAll(view.root());sidebarController.select(screen);sidebarController.setSignedIn(true);
            pageTitle.setText(screen.title());sessionLabel.setText(api.currentUser().email());logoutButton.setVisible(true);logoutButton.setManaged(true);
        }catch(IOException error){throw new IllegalStateException("Cannot load destination: "+screen,error);}
    }
    @FXML private void logout(){if(current instanceof ExamController exam&&!exam.confirmDiscard())return;api.logout();assignedExamActive=false;showAuth();}
    private void closeView(){if(current instanceof AuthController auth)auth.deactivate();else if(current instanceof ClassesController classes)classes.deactivate();else if(current instanceof ExamController exam)exam.close();else if(current instanceof AsyncController controller)controller.close();current=null;}
}
