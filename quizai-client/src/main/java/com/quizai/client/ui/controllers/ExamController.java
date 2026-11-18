package com.quizai.client.ui.controllers;

import com.quizai.common.api.ApiException;
import com.quizai.common.api.ApiModels.*;
import com.quizai.common.api.QuizApiClient;
import com.quizai.common.api.V2ApiClient;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Online assigned exams. Only a server ACK marks answers as saved or an attempt as submitted. */
public final class ExamController implements AutoCloseable {
    @FXML private VBox workspace;
    @FXML private Label message;
    @FXML private ProgressIndicator progress;
    private V2ApiClient api;
    private Runnable expired;
    private Consumer<Boolean> onExamState;
    private boolean active=true,busy,deadlineObserved;
    private Attempt attempt;
    private UUID submitRequestId;
    private final Map<UUID,UUID> startRequestIds=new HashMap<>();
    private final Set<UUID> activeAttemptIds=new HashSet<>();
    private final Map<UUID,AnswerState> answers=new LinkedHashMap<>();
    private final List<Node> answerEditors=new ArrayList<>();
    private Timeline countdown;
    private Label clock;
    private Button submit;
    private long serverAnchorNanos;
    private Instant serverAnchor;
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final class AnswerState {
        final ExamQuestion question;
        final Label status;
        String selected;
        long revision;
        long acknowledged;
        int pending;
        boolean failed;
        AnswerState(ExamQuestion question,Label status){this.question=question;this.status=status;selected=question.selectedOptionKey();revision=question.answerRevision();acknowledged=revision;}
        boolean dirty(){return failed||pending>0||acknowledged<revision;}
    }
    public void configure(QuizApiClient client,Runnable expired,Consumer<Boolean> onExamState) {
        this.api=client.v2();this.expired=expired;this.onExamState=onExamState;loadAssignments();
    }
    private void loadAssignments() {
        stopCountdown();attempt=null;answers.clear();
        run(api::studentAssignments,values->{
            activeAttemptIds.clear();values.stream().map(StudentAssignment::activeAttemptId).filter(Objects::nonNull).forEach(activeAttemptIds::add);
            onExamState.accept(!activeAttemptIds.isEmpty());
            workspace.getChildren().setAll(label("Bài kiểm tra và kết quả của bạn","page-title"),
                    label("Chỉ những câu đã được máy chủ xác nhận mới được chấm. Khi mất kết nối, hãy kiểm tra trạng thái lưu trước khi tiếp tục.","muted"),button("Làm mới từ máy chủ",this::loadAssignments));
            if(values.isEmpty())workspace.getChildren().add(label("Chưa có bài kiểm tra được giao.","muted"));
            for(StudentAssignment value:values) {
                VBox card=card(label(value.title(),"section-title"),label(date(value.opensAt())+" → "+date(value.closesAt())+" · "+value.durationMinutes()+" phút · Đã dùng "+value.attemptsUsed()+" / "+value.maxAttempts()+" lượt","caption"),
                        label("Điểm cao nhất: "+(value.scoreAvailable()?score(value.bestScore()):"Chưa công bố"),"small-heading"));
                if(value.activeAttemptId()!=null)card.getChildren().add(button("Tiếp tục lượt đang làm",()->loadAttempt(value.activeAttemptId())));
                else if(value.canStart())card.getChildren().add(button("Bắt đầu lượt mới",()->{
                    UUID requestId=startRequestIds.computeIfAbsent(value.id(),key->UUID.randomUUID());
                    run(()->api.start(value.id(),requestId),result->{startRequestIds.remove(value.id());showAttempt(result);});
                }));
                else card.getChildren().add(label("Hiện không thể bắt đầu lượt mới.","caption"));
                // The history endpoint returns only the student's own attempts and released fields.
                VBox history=new VBox(8);
                card.getChildren().add(button("Lịch sử / kết quả",()->run(()->api.studentAttempts(value.id()),attempts->{
                    history.getChildren().clear();
                    if(attempts.isEmpty())history.getChildren().add(label("Chưa có lượt làm.","muted"));
                    for(Attempt item:attempts)history.getChildren().add(button("Lượt "+item.attemptNo()+" · "+item.status()+" · "+(item.scoreAvailable()?score(item.normalizedScore()):"Chưa công bố điểm"),()->loadAttempt(item.id())));
                })));
                card.getChildren().add(history);
                if(value.answerAvailable())card.getChildren().add(button("Xem đáp án đã công bố",()->run(()->api.review(value.id()),questions->{
                    VBox review=new VBox(10);
                    for(ExamQuestion question:questions){
                        String correct=question.options().stream().filter(o->Objects.equals(o.key(),question.correctOptionKey())).map(SafeOption::text).findFirst().orElse("Chưa công bố");
                        review.getChildren().add(card(label(question.position()+". "+question.stem(),"small-heading"),label("Đáp án: "+correct,"muted")));
                    }
                    card.getChildren().add(review);
                })));
                workspace.getChildren().add(card);
            }
        });
    }
    private void loadAttempt(UUID id){run(()->api.attempt(id),this::showAttempt);}
    private void showAttempt(Attempt value) {
        stopCountdown();attempt=value;answers.clear();answerEditors.clear();deadlineObserved=false;submitRequestId=null;
        boolean inProgress="IN_PROGRESS".equals(value.status());
        if(inProgress)activeAttemptIds.add(value.id());else activeAttemptIds.remove(value.id());
        onExamState.accept(!activeAttemptIds.isEmpty());
        serverAnchor=value.serverTime();serverAnchorNanos=System.nanoTime();
        clock=label("", "section-title");
        workspace.getChildren().setAll(label("Lượt "+value.attemptNo()+" · "+value.status(),"page-title"),
                label("Hạn từ máy chủ: "+date(value.deadlineAt()),"caption"),clock,
                label("QuizAI không cung cấp hỗ trợ AI trong bài kiểm tra được giao.","muted"));
        if(!inProgress)workspace.getChildren().add(label("Điểm: "+(value.scoreAvailable()?score(value.normalizedScore()):"Chưa công bố"),"section-title"));
        for(ExamQuestion question:value.questions()) {
            Label status=label(question.serverSavedAt()==null?"Chưa có câu trả lời được xác nhận":"Đã được máy chủ xác nhận · "+date(question.serverSavedAt()),"caption");
            AnswerState answer=new AnswerState(question,status);answers.put(question.id(),answer);
            ToggleGroup choices=new ToggleGroup();VBox options=new VBox(9);
            int optionPosition=0;
            for(SafeOption option:question.options()) {
                RadioButton radio=new RadioButton((++optionPosition)+". "+option.text());radio.setWrapText(true);radio.setToggleGroup(choices);
                radio.setSelected(Objects.equals(question.selectedOptionKey(),option.key()));radio.setDisable(!inProgress);
                radio.setOnAction(e->change(answer,option.key()));options.getChildren().add(radio);
            }
            VBox card=card(label(question.position()+". "+question.stem(),"section-title"),options,status);
            answerEditors.add(options);
            if(inProgress){HBox actions=row(button("Bỏ chọn",()->{choices.selectToggle(null);change(answer,null);}),button("Gửi lại câu này",()->retry(answer)));card.getChildren().add(actions);answerEditors.add(actions);}
            if(value.answerAvailable()&&question.correctOptionKey()!=null)card.getChildren().add(label("Đáp án được công bố: "+question.options().stream().filter(o->o.key().equals(question.correctOptionKey())).map(SafeOption::text).findFirst().orElse("—"),"small-heading"));
            workspace.getChildren().add(card);
        }
        submit=button("Nộp những câu đã được xác nhận",this::confirmSubmit);submit.setDisable(!inProgress);
        workspace.getChildren().add(row(submit,button("Đọc lại trạng thái từ máy chủ",()->{
            if(confirmDiscard())loadAttempt(value.id());
        }),button("Danh sách bài",()->{if(confirmDiscard())loadAssignments();})));
        if(inProgress){
            if(!value.serverTime().isBefore(value.deadlineAt())){
                deadlineObserved=true;answerEditors.forEach(node->node.setDisable(true));updateSubmit();
                clock.setText("Đã đến hạn — chờ máy chủ chốt các câu đã xác nhận. Dùng Đọc lại trạng thái từ máy chủ để kiểm tra.");
            }else{countdown=new Timeline(new KeyFrame(Duration.seconds(1),e->tick()));countdown.setCycleCount(Timeline.INDEFINITE);countdown.play();tick();}
        }
    }
    private void change(AnswerState answer,String key) {
        if(!active||attempt==null||deadlineObserved||!"IN_PROGRESS".equals(attempt.status()))return;
        answer.selected=key;answer.revision++;answer.failed=false;send(answer);
    }
    private void retry(AnswerState answer){if(!deadlineObserved&&answer.pending==0&&answer.dirty())send(answer);}
    private void send(AnswerState answer) {
        long revision=answer.revision;String selected=answer.selected;UUID attemptId=attempt.id();answer.pending++;
        answer.status.setText("Đang gửi · chưa được xác nhận (phiên bản "+revision+")");updateSubmit();
        api.save(attemptId,answer.question.id(),selected,revision).whenComplete((ack,error)->Platform.runLater(()->{
            if(!active||attempt==null||!attempt.id().equals(attemptId)||answers.get(answer.question.id())!=answer)return;
            answer.pending--;
            if(error==null){
                answer.acknowledged=Math.max(answer.acknowledged,ack.answerRevision());
                if(ack.answerRevision()==answer.revision&&Objects.equals(ack.selectedOptionKey(),answer.selected)){
                    answer.failed=false;answer.status.setText("Đã được máy chủ xác nhận · "+date(ack.serverSavedAt()));
                }else if(answer.pending==0&&(ack.answerRevision()>=answer.revision||answer.acknowledged<answer.revision)){
                    answer.failed=true;answer.status.setText("Có thay đổi chưa được xác nhận. Hãy đọc lại trạng thái máy chủ.");
                }
            }else if(revision==answer.revision){
                answer.failed=true;answer.status.setText("Chưa được xác nhận — "+AuthController.friendly(error)+" Dùng Gửi lại câu này.");handleExpired(error);
            }
            updateSubmit();
        }));
    }
    private void updateSubmit(){if(submit!=null)submit.setDisable(busy||deadlineObserved||attempt==null||!"IN_PROGRESS".equals(attempt.status())||answers.values().stream().anyMatch(AnswerState::dirty));}
    private void tick(){
        if(!active||attempt==null)return;
        Instant estimated=serverAnchor.plusNanos(Math.max(0,System.nanoTime()-serverAnchorNanos));
        long remaining=Math.max(0,(java.time.Duration.between(estimated,attempt.deadlineAt()).toMillis()+999)/1000);
        if(!estimated.isBefore(attempt.deadlineAt())){
            deadlineObserved=true;stopCountdown();answerEditors.forEach(node->node.setDisable(true));clock.setText("Đã đến hạn — đang kiểm tra kết quả từ máy chủ.");workspace.setDisable(true);
            run(()->api.attempt(attempt.id()),value->{workspace.setDisable(false);showAttempt(value);if("IN_PROGRESS".equals(value.status()))message.setText("Đang chờ máy chủ chốt các câu đã xác nhận. Hãy đọc lại trạng thái.");});
        }else clock.setText(String.format("Còn %02d:%02d",remaining/60,remaining%60));
    }
    private void confirmSubmit(){
        if(answers.values().stream().anyMatch(AnswerState::dirty)){message.setText("Còn câu chưa được xác nhận. Gửi lại hoặc đọc trạng thái máy chủ trước khi nộp.");return;}
        Alert dialog=new Alert(Alert.AlertType.CONFIRMATION,"Nộp các câu đã được máy chủ xác nhận? Bạn không thể sửa sau khi nộp.",ButtonType.OK,ButtonType.CANCEL);
        dialog.initOwner(workspace.getScene().getWindow());dialog.setHeaderText("Nộp bài kiểm tra");
        dialog.showAndWait().filter(v->v==ButtonType.OK).ifPresent(v->{
            if(submitRequestId==null)submitRequestId=UUID.randomUUID();UUID requestId=submitRequestId;
            run(()->api.submit(attempt.id(),requestId),result->{showAttempt(result);message.setText("Máy chủ xác nhận trạng thái: "+result.status());});
        });
    }
    private <T> void run(Supplier<CompletableFuture<T>> action,Consumer<T> success){
        if(!active||busy)return;busy=true;workspace.setDisable(true);progress.setVisible(true);progress.setManaged(true);message.setText("Đang kết nối máy chủ…");
        CompletableFuture<T> future;try{future=action.get();}catch(RuntimeException error){future=CompletableFuture.failedFuture(error);}
        future.whenComplete((value,error)->Platform.runLater(()->{
            if(!active)return;busy=false;workspace.setDisable(false);progress.setVisible(false);progress.setManaged(false);
            if(error==null){message.setText("");success.accept(value);}else{
                message.setText(AuthController.friendly(error));handleExpired(error);
                if(workspace.getChildren().isEmpty())workspace.getChildren().add(button("Thử tải lại",()->run(action,success)));
            }updateSubmit();
        }));
    }
    private void handleExpired(Throwable error){while(error.getCause()!=null&&!(error instanceof ApiException))error=error.getCause();if(error instanceof ApiException e&&e.status()==401)expired.run();}
    public boolean confirmDiscard(){
        if(answers.values().stream().noneMatch(AnswerState::dirty))return true;
        Alert dialog=new Alert(Alert.AlertType.CONFIRMATION,"Có câu trả lời chưa được máy chủ xác nhận. Rời màn hình sẽ bỏ những thay đổi này; chỉ câu đã ACK được giữ.",ButtonType.OK,ButtonType.CANCEL);
        dialog.initOwner(workspace.getScene().getWindow());dialog.setHeaderText("Rời lượt đang làm?");return dialog.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
    private void stopCountdown(){if(countdown!=null){countdown.stop();countdown=null;}}
    @Override public void close(){active=false;stopCountdown();}
    private static Label label(String text,String style){Label value=new Label(text);value.setWrapText(true);value.getStyleClass().add(style);return value;}
    private static Button button(String text,Runnable action){Button value=new Button(text);value.setOnAction(e->action.run());return value;}
    private static VBox card(Node...nodes){VBox value=new VBox(12,nodes);value.getStyleClass().add("card");return value;}
    private static HBox row(Node...nodes){HBox value=new HBox(12,nodes);value.setAlignment(javafx.geometry.Pos.CENTER_LEFT);return value;}
    private static String date(Instant value){return value==null?"—":DATE.format(value);}
    private static String score(BigDecimal value){return value==null?"Chưa có điểm":value.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()+" / 10";}
}
