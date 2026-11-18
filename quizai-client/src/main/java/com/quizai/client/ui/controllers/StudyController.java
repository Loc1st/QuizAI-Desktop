package com.quizai.client.ui.controllers;

import com.quizai.client.ui.navigation.Screen;
import com.quizai.common.api.QuizApiClient;
import com.quizai.common.api.StudyApiClient;
import com.quizai.common.api.StudyModels.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Private study uses only authenticated server APIs and never exposes a Gemini key. */
public final class StudyController extends AsyncController {
    private StudyApiClient study;
    private List<Document> documents=List.of();
    private List<PrivateQuiz> quizzes=List.of();
    private List<Practice> history=List.of();
    private List<Conversation> conversations=List.of();
    private boolean practiceSaveUncertain;
    private Button completePractice;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    public void configure(QuizApiClient api,Runnable expired,Screen screen){
        configureApi(api,expired);study=api.study();
        if(screen==Screen.DOCUMENTS)loadDocuments();else if(screen==Screen.TUTOR)loadTutor();else loadQuizzes();
    }
    private void loadDocuments(){run(study::documents,values->{documents=values;renderDocuments();});}
    private void loadDocuments(String success){run(study::documents,values->{documents=values;renderDocuments();feedback.setText(success);});}
    private void renderDocuments(){
        TextField title=field("Tên tài liệu / bộ đề");TextArea text=new TextArea();text.setPromptText("Dán văn bản học tập (tối đa 100.000 ký tự)");text.setPrefRowCount(5);text.setWrapText(true);
        workspace.getChildren().setAll(label("Tài liệu và tạo câu hỏi AI","page-title"),
                label("Tài liệu và bộ đề trong màn hình này thuộc riêng tài khoản của bạn.","muted"),
                card(title,text,row(button("Lưu văn bản",()->{
                    if(title.getText().isBlank()||text.getText().isBlank()){feedback.setText("Nhập tên và nội dung tài liệu.");return;}
                    run(()->study.paste(title.getText().trim(),text.getText()),value->loadDocuments("Đã lưu văn bản trên máy chủ."));
                }),button("Nhập PDF / DOCX / TXT",this::chooseUpload),button("Làm mới",this::loadDocuments))));
        if(documents.isEmpty())workspace.getChildren().add(label("Chưa có tài liệu. Nhập tệp hoặc dán văn bản để bắt đầu.","muted"));
        for(Document document:documents)workspace.getChildren().add(card(label(documentName(document),"section-title"),label(document.inputType()+" · "+document.extractionStatus(),"caption")));
        ComboBox<Document> source=combo(documents,StudyController::documentName);source.setPromptText("Chọn tài liệu để tạo câu hỏi");
        TextField quizTitle=field("Tên bộ đề cá nhân");Spinner<Integer> count=new Spinner<>(1,20,5);ComboBox<String> difficulty=difficulty();
        workspace.getChildren().add(card(label("Tạo bộ đề riêng bằng Gemini","section-title"),source,quizTitle,row(label("Số câu", "caption"),count,difficulty),button("Tạo câu hỏi",()->{
            if(source.getValue()==null||quizTitle.getText().isBlank()){feedback.setText("Chọn tài liệu và nhập tên bộ đề.");return;}
            Map<String,Object> body=Map.of("documentId",source.getValue().id(),"title",quizTitle.getText().trim(),"count",count.getValue(),"difficulty",difficulty.getValue());
            run(()->study.generate(body),result->{feedback.setText("Máy chủ đã tạo "+result.questionVersionIds().size()+" câu. Mở Bộ đề cá nhân để luyện tập.");});
        })));
    }
    private void chooseUpload(){
        FileChooser chooser=new FileChooser();chooser.setTitle("Chọn tài liệu học tập (tối đa 5 MB)");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF, DOCX, TXT","*.pdf","*.docx","*.txt"));
        File file=chooser.showOpenDialog(workspace.getScene().getWindow());if(file!=null)run(()->study.upload(file.toPath()),value->loadDocuments("Đã nhập tài liệu trên máy chủ."));
    }
    private void loadQuizzes(){run(()->study.quizzes().thenCombine(study.history(),StudyLibrary::new),library->{quizzes=library.quizzes();history=library.history();renderQuizzes();});}
    private record StudyLibrary(List<PrivateQuiz> quizzes,List<Practice> history) { }
    private void renderQuizzes(){
        workspace.getChildren().setAll(label("Bộ đề cá nhân và luyện tập","page-title"),button("Làm mới",this::loadQuizzes));
        if(quizzes.isEmpty())workspace.getChildren().add(label("Chưa có bộ đề riêng. Mở Tài liệu học tập để tạo câu hỏi.","muted"));
        for(PrivateQuiz quiz:quizzes)workspace.getChildren().add(card(label(quiz.title(),"section-title"),label(quiz.status()+" · "+quiz.questionCount()+" câu","caption"),button("Bắt đầu luyện tập",()->run(()->study.start(quiz.id()),this::renderPractice))));
        workspace.getChildren().add(label("Lịch sử luyện tập", "section-title"));
        if(history.isEmpty())workspace.getChildren().add(label("Chưa có lượt luyện tập.","muted"));
        for(Practice practice:history){String title=quizzes.stream().filter(q->q.id().equals(practice.quizId())).map(PrivateQuiz::title).findFirst().orElse("Bộ đề cá nhân");
            workspace.getChildren().add(card(label(title,"small-heading"),label(practice.status()+" · "+DATE.format(practice.startedAt())+" · "+practiceScore(practice),"caption"),button("Mở lượt luyện tập",()->run(()->study.practice(practice.id()),this::renderPractice))));}
    }
    private void renderPractice(Practice practice){
        practiceSaveUncertain=false;
        completePractice=null;
        boolean completed="COMPLETED".equals(practice.status());
        workspace.getChildren().setAll(label("Luyện tập · "+practice.status(),"page-title"),label(completed?practiceScore(practice):"Lựa chọn được lưu khi máy chủ phản hồi. Nếu gặp lỗi, hãy đọc lại trạng thái trước khi hoàn thành.","muted"));
        VBox chatHost=new VBox(12);
        for(PracticeQuestion question:practice.questions()){
            ToggleGroup group=new ToggleGroup();VBox options=new VBox(9);
            for(PracticeOption option:question.options()){
                RadioButton choice=new RadioButton(option.position()+". "+option.text()+(completed&&Boolean.TRUE.equals(option.correct())?" ✓":""));choice.setWrapText(true);choice.setToggleGroup(group);choice.setSelected(option.id().equals(question.selectedOptionId()));choice.setDisable(completed);
                choice.setOnAction(e->run(()->study.save(practice.id(),question.id(),option.id()),saved->{renderPractice(saved);feedback.setText("Máy chủ đã xác nhận câu trả lời luyện tập.");},error->{
                    renderPractice(practice);practiceSaveUncertain=true;
                    workspace.getChildren().add(label("Thay đổi vừa chọn chưa được xác nhận. Đang hiển thị lựa chọn được xác nhận gần nhất; đọc lại trạng thái trước khi hoàn thành.","muted"));
                    if(completePractice!=null)completePractice.setDisable(true);
                }));options.getChildren().add(choice);
            }
            VBox card=card(label(question.position()+". "+question.stem(),"section-title"),options);
            if(completed)card.getChildren().add(button("Hỏi AI giải thích câu này",()->run(()->study.conversation("PRACTICE_SESSION",null,practice.id()).thenCompose(c->study.send(c.id(),"Giải thích câu hỏi này và đáp án đúng.",question.id()).thenApply(reply->c)),conversation->renderChat(chatHost,conversation))));
            workspace.getChildren().add(card);
        }
        HBox actions=row(button("Đọc lại trạng thái",()->run(()->study.practice(practice.id()),this::renderPractice)),button("Về bộ đề",this::loadQuizzes));
        if(!completed){completePractice=button("Hoàn thành luyện tập",()->{
            if(practiceSaveUncertain){feedback.setText("Đọc lại trạng thái máy chủ trước khi hoàn thành lượt có thay đổi chưa xác nhận.");return;}
            run(()->study.complete(practice.id()),value->{renderPractice(value);feedback.setText("Máy chủ đã xác nhận hoàn thành luyện tập.");});
        });actions.getChildren().add(completePractice);}
        workspace.getChildren().addAll(actions,chatHost);
        if(completed)workspace.getChildren().add(button("Mở hội thoại luyện tập",()->run(()->study.conversation("PRACTICE_SESSION",null,practice.id()),conversation->renderChat(chatHost,conversation))));
    }
    private void loadTutor(){run(()->study.documents().thenCombine(study.conversations(),TutorLibrary::new),library->{documents=library.documents();conversations=library.conversations();renderTutor();});}
    private record TutorLibrary(List<Document> documents,List<Conversation> conversations) { }
    private void renderTutor(){
        ComboBox<Document> pdf=combo(documents.stream().filter(d->"PDF".equals(d.inputType())).toList(),StudyController::documentName);pdf.setPromptText("Chọn PDF của bạn");VBox chat=new VBox(12);
        workspace.getChildren().setAll(label("Gia sư AI theo PDF", "page-title"),label("Chọn PDF đã nhập. Hội thoại thuộc riêng tài khoản; AI không khả dụng trong lúc có bài kiểm tra được giao đang làm.","muted"),
                row(pdf,button("Mở hội thoại PDF",()->{if(pdf.getValue()==null){feedback.setText("Chọn PDF đã nhập ở Tài liệu học tập.");return;}run(()->study.conversation("PDF_TUTOR",pdf.getValue().id(),null),c->renderChat(chat,c));}),button("Làm mới",this::loadTutor)),chat);
        for(Conversation conversation:conversations){String title="PDF_TUTOR".equals(conversation.type())?documents.stream().filter(d->d.id().equals(conversation.documentId())).map(StudyController::documentName).findFirst().orElse("PDF"):"Hội thoại luyện tập";
            workspace.getChildren().add(button(title+" · "+DATE.format(conversation.createdAt()),()->renderChat(chat,conversation)));}
    }
    private void renderChat(VBox host,Conversation conversation){run(()->study.messages(conversation.id()),messages->{
        host.getChildren().clear();host.getChildren().add(label("Hội thoại riêng · "+conversation.type(),"section-title"));
        for(Message message:messages)host.getChildren().add(card(label("assistant".equalsIgnoreCase(message.role())?"QuizAI":"Bạn","small-heading"),label(message.content(),"muted")));
        TextArea prompt=new TextArea();prompt.setPromptText("Nhập câu hỏi học tập");prompt.setWrapText(true);prompt.setPrefRowCount(3);
        host.getChildren().addAll(prompt,row(button("Gửi câu hỏi",()->{if(prompt.getText().isBlank()){feedback.setText("Nhập câu hỏi.");return;}run(()->study.send(conversation.id(),prompt.getText().trim(),null),reply->renderChat(host,conversation));}),button("Làm mới lịch sử",()->renderChat(host,conversation))));
    });}
    private static String documentName(Document document){return document.fileName()==null?"Văn bản · "+DATE.format(document.createdAt()):document.fileName();}
    private static String practiceScore(Practice practice){return practice.correctCount()==null?"Chưa hoàn thành":practice.correctCount()+" / "+practice.totalQuestions()+" câu đúng";}
    private static ComboBox<String> difficulty(){ComboBox<String> result=new ComboBox<>();result.getItems().setAll("EASY","MEDIUM","HARD");result.setValue("MEDIUM");return result;}
    private static <T> ComboBox<T> combo(List<T> values,Function<T,String> name){ComboBox<T> result=new ComboBox<>();result.getItems().setAll(values);result.setConverter(new StringConverter<>(){public String toString(T value){return value==null?"":name.apply(value);}public T fromString(String value){return null;}});return result;}
}
