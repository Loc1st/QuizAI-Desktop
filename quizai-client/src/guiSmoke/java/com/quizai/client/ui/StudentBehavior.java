package com.quizai.client.ui;

import com.quizai.client.ui.controllers.ExamController;
import com.quizai.client.ui.controllers.StudyController;
import com.quizai.client.ui.navigation.Screen;
import com.quizai.common.api.QuizApiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Native JavaFX controls + real HTTP transport to an isolated loopback fixture.
 * Does not connect to the application backend, PostgreSQL, Gemini or any external host. */
public final class StudentBehavior {
    private static final String ID="00000000-0000-0000-0000-000000000001";
    private static final String SECOND="00000000-0000-0000-0000-000000000002";
    private static final String THIRD="00000000-0000-0000-0000-000000000003";
    private static final String NOW="2026-11-18T00:00:00Z";
    private static volatile String mode="practice";
    private static final AtomicInteger saves=new AtomicInteger();
    private static final AtomicInteger reads=new AtomicInteger();
    private static final AtomicBoolean practiceCompleted=new AtomicBoolean();
    private static volatile CountDownLatch oldSaveRelease=new CountDownLatch(0);
    private static final AtomicBoolean oldSaveResponded=new AtomicBoolean();
    private static Stage stage;
    private static Parent view;
    private static AutoCloseable controller;

    public static void main(String[] args) throws Exception {
        HttpServer fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fixture.setExecutor(Executors.newCachedThreadPool(action->{Thread worker=new Thread(action,"student-http-fixture");worker.setDaemon(true);return worker;}));
        fixture.createContext("/",StudentBehavior::respond);
        fixture.start();
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            QuizApiClient api=new QuizApiClient(URI.create("http://127.0.0.1:"+fixture.getAddress().getPort()),"STUDENT");
            api.login("student@fixture.invalid","fixture-only").get(5,TimeUnit.SECONDS);
            initialStudyReadFailureCanRetry(api);
            failedPracticeSaveRestoresConfirmedSelection(api);
            completedPracticeHasServerScoreAndHistory(api);
            examSaveFailureBlocksSubmit(api);
            newerExamAckSurvivesOlderResponse(api);
            mismatchedExamAckCannotConfirmSelection(api);
            timeoutReadFailureKeepsAttemptLocked(api);
            timeoutStaleServerStateDoesNotPollForever(api);
            terminalHistoryKeepsOtherExamAiBlocked(api);
            System.out.println("STUDENT_JAVAFX_HTTP_FIXTURE_PASS (native controls + loopback fixtures only; no PostgreSQL/Gemini/backend E2E claim)");
        } finally {
            fx(()->{if(controller!=null)controller.close();if(stage!=null)stage.close();return null;});
            fixture.stop(0);Platform.exit();
        }
    }

    private static void initialStudyReadFailureCanRetry(QuizApiClient api) throws Exception {
        mode="initial-failure";reads.set(0);
        fx(()->{open("/fxml/study.fxml");((StudyController)controller).configure(api,()->{throw new AssertionError("Unexpected expiry");},Screen.PRACTICE);return null;});
        await(()->!progress(),"initial load failure");
        check(fx(()->nodes(Button.class).stream().anyMatch(b->b.getText().equals("Thử tải lại"))),"Initial study load failure must provide a retry action");
        click("Thử tải lại");await(()->nodes(Button.class).stream().anyMatch(b->b.getText().equals("Bắt đầu luyện tập")),"initial read retry");
        System.out.println("PASS initial study read failure/retry without leaving screen");
    }

    private static void failedPracticeSaveRestoresConfirmedSelection(QuizApiClient api) throws Exception {
        mode="practice";saves.set(0);showStudy(api);
        click("Bắt đầu luyện tập");await(()->!nodes(RadioButton.class).isEmpty(),"practice question");
        fx(()->{nodes(RadioButton.class).get(1).fire();return null;});
        await(()->saves.get()==1&&!progress(),"failed practice save response");
        check(fx(()->nodes(RadioButton.class).get(0).isSelected()),"Failed practice save must restore the server-confirmed option");
        check(!fx(()->nodes(RadioButton.class).get(1).isSelected()),"Rejected practice choice must not look saved");
        check(fx(()->labelText().contains("chưa")),"Practice save failure must explain unconfirmed state");
        snapshot("practice-save-not-confirmed");
        click("Đọc lại trạng thái");await(()->!progress(),"practice reload");
        fx(()->{nodes(RadioButton.class).get(1).fire();return null;});
        await(()->saves.get()==2&&!progress(),"practice retry ACK");
        check(fx(()->nodes(RadioButton.class).get(1).isSelected()),"Retry must show the server ACKed practice choice");
        System.out.println("PASS practice rejected save restores confirmed selection; explicit reload/retry ACK");
    }

    private static void completedPracticeHasServerScoreAndHistory(QuizApiClient api) throws Exception {
        click("Hoàn thành luyện tập");await(()->labelText().contains("COMPLETED"),"completed practice");
        check(fx(()->nodes(RadioButton.class).stream().allMatch(Node::isDisabled)),"Completed practice answers must be read only");
        check(fx(()->labelText().contains("1 / 1")),"Completed practice score must come from response");
        snapshot("practice-completed");
        click("Về bộ đề");await(()->labelText().contains("Lịch sử luyện tập"),"history refresh");
        click("Mở lượt luyện tập");await(()->labelText().contains("COMPLETED"),"history reopen");
        System.out.println("PASS practice complete/server score/history reopen");
    }

    private static void examSaveFailureBlocksSubmit(QuizApiClient api) throws Exception {
        mode="exam";saves.set(0);reads.set(0);showExam(api,new AtomicBoolean());
        click("Tiếp tục lượt đang làm");await(()->!nodes(RadioButton.class).isEmpty(),"assigned attempt");
        fx(()->{nodes(RadioButton.class).get(0).fire();return null;});
        await(()->saves.get()==1&&labelText().contains("Chưa được xác nhận"),"failed assigned save");
        check(fx(()->button("Nộp những câu đã được xác nhận").isDisabled()),"Unconfirmed assigned answer must block submission");
        snapshot("exam-save-not-confirmed");
        click("Gửi lại câu này");await(()->saves.get()==2&&labelText().contains("Đã được máy chủ xác nhận"),"assigned save retry");
        check(!fx(()->button("Nộp những câu đã được xác nhận").isDisabled()),"ACK should enable submission");
        System.out.println("PASS assigned save error/retry/ACK submit guard");
    }

    private static void timeoutReadFailureKeepsAttemptLocked(QuizApiClient api) throws Exception {
        mode="timeout";reads.set(0);showExam(api,new AtomicBoolean());
        click("Tiếp tục lượt đang làm");
        await(()->reads.get()>=2&&!progress(),"deadline status read failure");
        check(fx(()->nodes(RadioButton.class).stream().allMatch(Node::isDisabled)),"Deadline must lock choices after failed status refresh");
        check(fx(()->button("Nộp những câu đã được xác nhận").isDisabled()),"Deadline must disable manual submit after status failure");
        check(!fx(()->button("Đọc lại trạng thái từ máy chủ").isDisabled()),"Deadline failure must permit explicit state retry");
        snapshot("exam-timeout-status-unavailable");
        System.out.println("PASS timeout status read error preserves lock and permits retry");
    }

    private static void newerExamAckSurvivesOlderResponse(QuizApiClient api) throws Exception {
        mode="reordered-save";saves.set(0);reads.set(0);oldSaveRelease=new CountDownLatch(1);oldSaveResponded.set(false);
        showExam(api,new AtomicBoolean());click("Tiếp tục lượt đang làm");
        await(()->!nodes(RadioButton.class).isEmpty(),"reordered save attempt");
        fx(()->{nodes(RadioButton.class).get(0).fire();return null;});
        await(()->saves.get()==1,"old request held by fixture");
        fx(()->{nodes(RadioButton.class).get(1).fire();return null;});
        await(()->saves.get()==2&&labelText().contains("Đã được máy chủ xác nhận"),"newest save ACK arrives first");
        oldSaveRelease.countDown();
        await(()->oldSaveResponded.get()&&!button("Nộp những câu đã được xác nhận").isDisabled(),"older ACK must preserve newest ACK status");
        check(fx(()->nodes(RadioButton.class).get(1).isSelected()),"Older ACK must not replace newest choice");
        check(!fx(()->labelText().contains("Có thay đổi chưa được xác nhận")),"Older success ACK must not mark a newer confirmed answer dirty");
        System.out.println("PASS newest assigned ACK remains confirmed when older response arrives later");
    }

    private static void mismatchedExamAckCannotConfirmSelection(QuizApiClient api) throws Exception {
        mode="mismatched-save";saves.set(0);reads.set(0);showExam(api,new AtomicBoolean());
        click("Tiếp tục lượt đang làm");await(()->!nodes(RadioButton.class).isEmpty(),"mismatched ACK attempt");
        fx(()->{nodes(RadioButton.class).get(0).fire();return null;});
        await(()->saves.get()==1&&labelText().contains("Có thay đổi chưa được xác nhận"),"mismatched ACK rejection");
        check(fx(()->button("Nộp những câu đã được xác nhận").isDisabled()),"ACK of a different choice must not permit submit");
        System.out.println("PASS mismatched assigned ACK never confirms current selection");
    }

    private static void terminalHistoryKeepsOtherExamAiBlocked(QuizApiClient api) throws Exception {
        mode="history";AtomicBoolean blocked=new AtomicBoolean();showExam(api,blocked);
        click("Lịch sử / kết quả");await(()->nodes(Button.class).stream().anyMatch(b->b.getText().startsWith("Lượt 1")),"terminal history");
        fx(()->{nodes(Button.class).stream().filter(b->b.getText().startsWith("Lượt 1")).findFirst().orElseThrow().fire();return null;});
        await(()->labelText().contains("SUBMITTED"),"terminal attempt");
        check(blocked.get(),"Opening a completed attempt must keep AI blocked when another assignment has an active attempt");
        System.out.println("PASS terminal history preserves AI block for another active assignment");
    }

    private static void timeoutStaleServerStateDoesNotPollForever(QuizApiClient api) throws Exception {
        mode="timeout-stale";reads.set(0);showExam(api,new AtomicBoolean());
        click("Tiếp tục lượt đang làm");await(()->reads.get()>=2&&!progress(),"server timeout state pending");
        Thread.sleep(300);
        check(reads.get()<=2,"A past-deadline IN_PROGRESS response must stop automatic polling and permit explicit retry");
        check(fx(()->nodes(RadioButton.class).stream().allMatch(Node::isDisabled)),"Past-deadline server state must keep answers locked");
        System.out.println("PASS stale IN_PROGRESS deadline response does not poll indefinitely");
    }

    private static void showStudy(QuizApiClient api) throws Exception {
        fx(()->{open("/fxml/study.fxml");((StudyController)controller).configure(api,()->{throw new AssertionError("Unexpected session expiry");},Screen.PRACTICE);return null;});
        await(()->!progress()&&nodes(Button.class).stream().anyMatch(b->b.getText().equals("Bắt đầu luyện tập")),"private quiz list");
    }
    private static void showExam(QuizApiClient api,AtomicBoolean blocked) throws Exception {
        fx(()->{open("/fxml/exams.fxml");((ExamController)controller).configure(api,()->{throw new AssertionError("Unexpected session expiry");},blocked::set);return null;});
        await(()->!progress()&&nodes(Button.class).stream().anyMatch(b->b.getText().equals("Tiếp tục lượt đang làm")),"assignment list");
    }
    private static void open(String resource) throws Exception {
        if(controller!=null)controller.close();if(stage!=null)stage.close();
        FXMLLoader loader=new FXMLLoader(StudentBehavior.class.getResource(resource));view=loader.load();controller=loader.getController();
        BorderPane shell=new BorderPane(view);shell.getStyleClass().add("theme-light");
        Scene scene=new Scene(shell,1000,760);scene.getStylesheets().add(StudentBehavior.class.getResource("/css/theme.css").toExternalForm());
        stage=new Stage();stage.setScene(scene);stage.show();view.applyCss();view.layout();
        check(stage.isShowing(),"Native JavaFX window must actually show");
    }
    private static void click(String text) throws Exception {fx(()->{Button value=button(text);check(!value.isDisabled(),"Button disabled: "+text);value.fire();return null;});}
    private static Button button(String text){return nodes(Button.class).stream().filter(b->b.getText().equals(text)).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));}
    private static String labelText(){return nodes(Label.class).stream().map(Label::getText).reduce("",(a,b)->a+"\n"+b);}
    private static boolean progress(){return nodes(ProgressIndicator.class).stream().anyMatch(Node::isVisible);}
    private static <T extends Node> List<T> nodes(Class<T> type){List<T> found=new ArrayList<>();walk(view,type,found);return found;}
    private static <T extends Node> void walk(Node node,Class<T> type,List<T> found){if(type.isInstance(node))found.add(type.cast(node));if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())walk(child,type,found);}
    private static <T> T fx(Callable<T> action) throws Exception {if(Platform.isFxApplicationThread())return action.call();FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(5,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition,String description) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<end){if(fx(condition::getAsBoolean))return;Thread.sleep(20);}
        throw new AssertionError("Timed out waiting for "+description+"; "+fx(StudentBehavior::labelText));
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void snapshot(String name) throws Exception {
        Path directory=Path.of(System.getProperty("quizai.behavior.output","build/student-gui-behavior"));Files.createDirectories(directory);
        fx(()->{
            view.applyCss();view.layout();var rendered=stage.getScene().snapshot(null);
            BufferedImage png=new BufferedImage((int)rendered.getWidth(),(int)rendered.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,rendered.getPixelReader().getArgb(x,y));
            ImageIO.write(png,"png",directory.resolve(name+".png").toFile());return null;
        });
    }

    private static void respond(HttpExchange exchange) throws java.io.IOException {
        String path=exchange.getRequestURI().getPath();String method=exchange.getRequestMethod();String body="[]";int status=200;
        exchange.getRequestBody().readAllBytes();
        if(path.equals("/api/auth/login"))body="{\"token\":\"fixture-only-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\""+ID+"\",\"email\":\"student@fixture.invalid\",\"role\":\"STUDENT\"}}";
        else if(path.equals("/api/v2/practice/quizzes")){if(mode.equals("initial-failure")&&reads.incrementAndGet()==1){status=503;body="{}";}else body="[{\"id\":\""+ID+"\",\"title\":\"Fixture quiz\",\"status\":\"READY\",\"questionCount\":1}]";}
        else if(path.equals("/api/v2/practice"))body=practiceCompleted.get()?"["+practice(true,true)+"]":"[]";
        else if(path.contains("/practice/")&&path.contains("/answers/")){int count=saves.incrementAndGet();if(count==1){status=503;body="{}";}else body=practice(true,false);}
        else if(path.contains("/practice/")){if(path.endsWith("/complete"))practiceCompleted.set(true);body=practice(saves.get()>=2,practiceCompleted.get());}
        else if(path.equals("/api/v2/student/assignments"))body=assignments();
        else if(path.endsWith("/attempts")&&path.contains("/assignments/"))body="["+attempt("SUBMITTED",SECOND,false)+"]";
        else if(path.contains("/student/attempts/")&&method.equals("PUT")){
            int count=saves.incrementAndGet();
            if(mode.equals("reordered-save")){
                if(count==1)try{if(!oldSaveRelease.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Fixture old response was never released");}catch(InterruptedException error){Thread.currentThread().interrupt();throw new java.io.IOException(error);}
                body="{\"assignmentQuestionId\":\""+THIRD+"\",\"selectedOptionKey\":\""+(count==1?"A":"B")+"\",\"answerRevision\":"+count+",\"serverSavedAt\":\""+NOW+"\"}";
            }else if(mode.equals("mismatched-save"))body="{\"assignmentQuestionId\":\""+THIRD+"\",\"selectedOptionKey\":\"B\",\"answerRevision\":1,\"serverSavedAt\":\""+NOW+"\"}";
            else if(count==1){status=503;body="{}";}else body="{\"assignmentQuestionId\":\""+THIRD+"\",\"selectedOptionKey\":\"A\",\"answerRevision\":1,\"serverSavedAt\":\""+NOW+"\"}";
        }
        else if(path.contains("/student/attempts/")){int count=reads.incrementAndGet();if(mode.equals("timeout")&&count>1){status=503;body="{}";}else body=attempt(mode.equals("history")?"SUBMITTED":"IN_PROGRESS",mode.equals("history")?SECOND:ID,mode.startsWith("timeout"));}
        else {status=404;body="{}";}
        byte[] data=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data);exchange.close();
        if(mode.equals("reordered-save")&&path.contains("/answers/")&&body.contains("\"answerRevision\":1"))oldSaveResponded.set(true);
    }
    private static String practice(boolean second,boolean completed){return "{\"id\":\""+ID+"\",\"quizId\":\""+ID+"\",\"status\":\""+(completed?"COMPLETED":"IN_PROGRESS")+"\",\"startedAt\":\""+NOW+"\",\"correctCount\":"+(completed?"1":"null")+",\"totalQuestions\":1,\"questions\":[{\"id\":\""+THIRD+"\",\"position\":1,\"stem\":\"Practice fixture question\",\"selectedOptionId\":\""+(second?SECOND:ID)+"\",\"options\":[{\"id\":\""+ID+"\",\"position\":1,\"text\":\"First\",\"correct\":false},{\"id\":\""+SECOND+"\",\"position\":2,\"text\":\"Second\",\"correct\":true}]}]}";}
    private static String assignments(){return "[{\"id\":\""+ID+"\",\"recipientId\":\""+ID+"\",\"classId\":\""+ID+"\",\"title\":\"Fixture assignment\",\"opensAt\":\""+NOW+"\",\"closesAt\":\"2099-01-01T00:00:00Z\",\"durationMinutes\":20,\"maxAttempts\":2,\"attemptsUsed\":1,\"activeAttemptId\":\""+ID+"\",\"scoreAvailable\":false,\"answerAvailable\":false}]";}
    private static String attempt(String state,String id,boolean timeout){Instant server=Instant.now();return "{\"id\":\""+id+"\",\"assignmentId\":\""+ID+"\",\"attemptNo\":1,\"status\":\""+state+"\",\"startedAt\":\""+NOW+"\",\"deadlineAt\":\""+server.plusMillis(timeout?(reads.get()>1?-1:250):60000)+"\",\"serverTime\":\""+server+"\",\"scoreAvailable\":false,\"answerAvailable\":false,\"questions\":[{\"id\":\""+THIRD+"\",\"position\":1,\"stem\":\"Assigned fixture question\",\"answerRevision\":0,\"options\":[{\"key\":\"A\",\"text\":\"First\"},{\"key\":\"B\",\"text\":\"Second\"}]}]}";}
}
