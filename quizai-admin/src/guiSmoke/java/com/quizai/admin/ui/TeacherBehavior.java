package com.quizai.admin.ui;

import com.quizai.admin.ui.controllers.TeacherWorkspaceController;
import com.quizai.admin.ui.navigation.Screen;
import com.quizai.common.api.ApiModels.Bank;
import com.quizai.common.api.ApiModels.ClassView;
import com.quizai.common.api.QuizApiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;

/** Native configured controllers using actual HTTP to a loopback fixture only.
 * This never connects to the application backend, PostgreSQL, Gemini or external hosts. */
public final class TeacherBehavior {
    private static final String TEACHER="00000000-0000-0000-0000-000000000001";
    private static final String CLASS="00000000-0000-0000-0000-000000000002";
    private static final String BANK_A="00000000-0000-0000-0000-000000000003";
    private static final String BANK_B="00000000-0000-0000-0000-000000000004";
    private static final String QUESTION="00000000-0000-0000-0000-000000000005";
    private static final String OLD="00000000-0000-0000-0000-000000000006";
    private static final String LATEST="00000000-0000-0000-0000-000000000007";
    private static final String CLASS_SECOND="00000000-0000-0000-0000-000000000008";
    private static final String ASSIGNMENT="00000000-0000-0000-0000-000000000009";
    private static final String OTHER_STUDENT="00000000-0000-0000-0000-000000000010";
    private static final String NOW="2026-11-18T00:00:00Z";
    private static final AtomicBoolean classCreated=new AtomicBoolean(),bankCreated=new AtomicBoolean();
    private static final AtomicInteger historyReads=new AtomicInteger(),summaryReads=new AtomicInteger();
    private static final AtomicReference<String> questionQuery=new AtomicReference<>(),quizBody=new AtomicReference<>();
    private static final AtomicReference<Throwable> fixtureFailure=new AtomicReference<>();
    private static final AtomicInteger memberReads=new AtomicInteger(),resultReads=new AtomicInteger();
    private static final AtomicBoolean assignmentMode=new AtomicBoolean(),published=new AtomicBoolean();
    private static final AtomicReference<String> assignmentBody=new AtomicReference<>();
    private static Stage stage;
    private static Parent view;
    private static TeacherWorkspaceController controller;
    private static long deadline;

    public static void main(String[] args) throws Exception {
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
        HttpServer fixture=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        fixture.createContext("/",TeacherBehavior::respond);fixture.start();
        Platform.startup(()->Platform.setImplicitExit(false));
        try {
            QuizApiClient api=new QuizApiClient(URI.create("http://127.0.0.1:"+fixture.getAddress().getPort()),"TEACHER");
            api.login("teacher@fixture.invalid","fixture-only").get(5,TimeUnit.SECONDS);
            classCreateRendersServerCode(api);
            bankCreateRefreshSelectsServerBank(api);
            questionOptionBoundsAreEnforced();
            approvedHistoricalSelectionSurvivesBankChange();
            summaryRendersOnlyServerAggregates(api);
            assignmentCreatePublishAndResultsUseServerState(api);
            check(fixtureFailure.get()==null,"Fixture request failure: "+fixtureFailure.get());
            System.out.println("TEACHER_JAVAFX_HTTP_FIXTURE_PASS (native configured controllers + loopback fixtures only; no PostgreSQL/Gemini/backend E2E claim)");
        } finally {
            fx(()->{if(controller!=null)controller.close();if(stage!=null)stage.close();return null;});
            fixture.stop(0);Platform.exit();
        }
    }

    private static void classCreateRendersServerCode(QuizApiClient api) throws Exception {
        open(api,Screen.CLASSES);await(()->!progress()&&hasButton("Tạo lớp"),"class load");
        fx(()->{field("Tên lớp").setText("Fixture class");button("Tạo lớp").fire();return null;});
        await(()->classCreated.get()&&!progress()&&labelText().contains("FIXTURE-CODE"),"create class response/code");
        check(fx(()->labelText().contains("Fixture class")),"Created class must render its server response");
        System.out.println("PASS teacher create class sends name and renders server join code");
    }

    private static void bankCreateRefreshSelectsServerBank(QuizApiClient api) throws Exception {
        open(api,Screen.BANK);await(()->!progress()&&hasButton("Tạo ngân hàng"),"bank load");
        fx(()->{field("Tên ngân hàng mới").setText("Fixture second bank");button("Tạo ngân hàng").fire();return null;});
        await(()->bankCreated.get()&&!progress()&&("bankId="+BANK_B).equals(questionQuery.get()),"new bank refresh");
        check(fx(()->((Bank)combo("teacher-bank-filter").getValue()).id().toString().equals(BANK_B)),"New bank must stay selected after refreshed list");
        check(fx(()->labelText().contains("Đã tạo ngân hàng: Fixture second bank")),"Create success must survive refresh");
        System.out.println("PASS teacher create bank refresh keeps server-created bank selected and success visible");
    }

    private static void questionOptionBoundsAreEnforced() throws Exception {
        fx(()->{
            check(nodes(RadioButton.class).size()==4,"Manual MCQ starts with four options");
            for(int i=0;i<6;i++)button("Thêm lựa chọn").fire();
            check(nodes(RadioButton.class).size()==10,"MCQ must permit ten options");
            check(button("Thêm lựa chọn").isDisabled(),"Add must disable at ten options");
            check(button("Thêm lựa chọn").getTooltip()!=null,"Disabled add must explain its limit");
            for(int i=0;i<8;i++)button("Xóa lựa chọn").fire();
            check(nodes(RadioButton.class).size()==2,"MCQ must permit two options");
            check(nodes(Button.class).stream().filter(b->b.getText().equals("Xóa lựa chọn")).allMatch(Node::isDisabled),"Remove must disable at two options");
            return null;
        });
        System.out.println("PASS teacher MCQ add/remove enforces 2–10 options in native controls");
    }

    private static void approvedHistoricalSelectionSurvivesBankChange() throws Exception {
        selectBank(BANK_A);await(()->!progress()&&hasButton("Lịch sử phiên bản"),"first bank questions");
        click("Lịch sử phiên bản");await(()->historyReads.get()==1&&!progress()&&hasSelection(OLD),"version history");
        fx(()->{
            CheckBox old=selection(OLD);check(!old.isDisabled(),"Older approved version must be selectable");old.fire();
            check(selection(LATEST).isDisabled(),"Newest draft must remain disabled with approval reason");return null;
        });
        selectBank(BANK_B);await(()->!progress()&&("bankId="+BANK_B).equals(questionQuery.get())&&!hasButton("Lịch sử phiên bản"),"second bank");
        check(fx(()->labelText().contains("Đã chọn 1 phiên bản")&&labelText().contains("Approved historical fixture")),"Changing banks must retain explicit older-version selection");
        fx(()->{field("Tên bộ đề").setText("Historical version quiz");button("Tạo bộ đề").fire();return null;});
        await(()->quizBody.get()!=null&&!progress()&&labelText().contains("Đã tạo bộ đề"),"quiz creation using older version");
        check(quizBody.get().contains(OLD)&&!quizBody.get().contains(LATEST),"Quiz POST must contain selected historical version, without the latest draft");
        System.out.println("PASS teacher history exposes older APPROVED version; cross-bank selection reaches quiz POST unchanged");
    }

    private static void summaryRendersOnlyServerAggregates(QuizApiClient api) throws Exception {
        open(api,Screen.SUMMARY);await(()->!progress()&&hasCombo("teacher-summary-class"),"summary class list");
        fx(()->{ComboBox<?> groups=combo("teacher-summary-class");check(groups.getItems().size()==1,"Summary uses returned classes");check(groups.getItems().getFirst() instanceof ClassView,"Configured controller must use class DTO");groups.getSelectionModel().selectFirst();return null;});
        await(()->summaryReads.get()==1&&!progress()&&labelText().contains("summary-student@fixture.invalid"),"study-summary response");
        check(fx(()->labelText().contains("75%")&&labelText().contains("Lượt luyện tập đã hoàn thành: 2")&&labelText().contains("Chưa có dữ liệu")),"Summary must render server counts, ratio and null-accuracy empty state");
        check(fx(()->labelText().contains("Không hiển thị tài liệu")),"Summary must explain student privacy");
        System.out.println("PASS teacher SUMMARY calls class study-summary endpoint and renders actual aggregate/null data");
    }

    private static void assignmentCreatePublishAndResultsUseServerState(QuizApiClient api) throws Exception {
        assignmentMode.set(true);open(api,Screen.ASSIGN);
        await(()->!progress()&&hasCombo("teacher-assignment-class"),"assignment form load");
        fx(()->{combo("teacher-assignment-class").getSelectionModel().selectFirst();return null;});
        await(()->memberReads.get()==1&&!progress()&&hasCheckBox("first-class@fixture.invalid"),"first class members");
        fx(()->{combo("teacher-assignment-class").getSelectionModel().select(1);return null;});
        await(()->memberReads.get()==2&&!progress()&&hasCheckBox("selected-recipient@fixture.invalid"),"changed class members");
        check(!fx(()->hasCheckBox("first-class@fixture.invalid")),"Changing class must remove stale recipient controls");
        fx(()->{
            field("Tên bài kiểm tra").setText("Fixture assigned test");
            combo("teacher-assignment-quiz").getSelectionModel().selectFirst();
            field("Mở: yyyy-MM-dd HH:mm").setText("2098-01-01 09:00");
            field("Đóng: yyyy-MM-dd HH:mm").setText("2098-01-01 10:30");
            setSpinner("teacher-assignment-duration",35);setSpinner("teacher-assignment-max-attempts",3);
            field("Công bố điểm: yyyy-MM-dd HH:mm (để trống: giữ kín)").setText("2098-01-01 10:35");
            field("Công bố đáp án: yyyy-MM-dd HH:mm (để trống: giữ kín)").setText("2098-01-01 10:40");
            combo("teacher-assignment-release-mode").getSelectionModel().selectFirst();
            checkBox("Giao cho tất cả thành viên hiện tại").fire();
            checkBox("selected-recipient@fixture.invalid").fire();
            button("Tạo bản nháp bài giao").fire();return null;
        });
        await(()->assignmentBody.get()!=null&&!progress()&&hasButton("Giao bài / đóng băng đề"),"created draft assignment reload");
        check(fx(()->labelText().contains("Fixture assigned test")&&labelText().contains("DRAFT")&&labelText().contains("35 phút · 3 lượt")),"Created assignment must render server draft configuration");
        click("Giao bài / đóng băng đề");
        await(()->published.get()&&!progress()&&labelText().contains("PUBLISHED")&&!hasButton("Giao bài / đóng băng đề"),"publish and canonical assignment reload");
        click("Xem kết quả");
        await(()->resultReads.get()==1&&!progress()&&labelText().contains("selected-recipient@fixture.invalid"),"server assignment results");
        check(fx(()->labelText().contains("Điểm cao nhất: 7.5 / 10")&&labelText().contains("Lượt 1 · SUBMITTED · 7.5 / 10")),"Result controls must display server best score and attempt history");
        System.out.println("PASS teacher class switch reloads recipients; assignment POST preserves selected recipient/time/duration/max/release; draft publish and GET results render server state");
    }

    private static void open(QuizApiClient api,Screen screen) throws Exception {
        fx(()->{
            if(controller!=null)controller.close();if(stage!=null)stage.close();
            FXMLLoader loader=new FXMLLoader(TeacherBehavior.class.getResource("/fxml/workspace.fxml"));view=loader.load();controller=loader.getController();
            stage=new Stage();stage.setScene(new Scene(view,1280,850));stage.show();view.applyCss();view.layout();
            check(stage.isShowing(),"Native teacher stage must be showing");
            controller.configure(api,()->{throw new AssertionError("Unexpected session expiry");},screen,s->{});return null;
        });
    }
    private static void selectBank(String id) throws Exception {fx(()->{ComboBox<?> filter=combo("teacher-bank-filter");for(int i=0;i<filter.getItems().size();i++)if(((Bank)filter.getItems().get(i)).id().toString().equals(id)){filter.getSelectionModel().select(i);return null;}throw new AssertionError("Bank missing: "+id);});}
    private static void click(String text) throws Exception {fx(()->{Button value=button(text);check(!value.isDisabled(),"Button disabled: "+text);value.fire();return null;});}
    private static TextField field(String prompt){return nodes(TextField.class).stream().filter(f->prompt.equals(f.getPromptText())).findFirst().orElseThrow();}
    private static boolean hasButton(String text){return nodes(Button.class).stream().anyMatch(b->b.getText().equals(text));}
    private static Button button(String text){return nodes(Button.class).stream().filter(b->b.getText().equals(text)).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));}
    private static boolean hasCombo(String id){return nodes(ComboBox.class).stream().anyMatch(c->id.equals(c.getId()));}
    private static ComboBox<?> combo(String id){return nodes(ComboBox.class).stream().filter(c->id.equals(c.getId())).findFirst().orElseThrow();}
    private static boolean hasSelection(String id){return nodes(CheckBox.class).stream().anyMatch(c->("teacher-select-version-"+id).equals(c.getId()));}
    private static CheckBox selection(String id){return nodes(CheckBox.class).stream().filter(c->("teacher-select-version-"+id).equals(c.getId())).findFirst().orElseThrow();}
    private static boolean hasCheckBox(String text){return nodes(CheckBox.class).stream().anyMatch(c->text.equals(c.getText()));}
    private static CheckBox checkBox(String text){return nodes(CheckBox.class).stream().filter(c->text.equals(c.getText())).findFirst().orElseThrow();}
    @SuppressWarnings("unchecked") private static void setSpinner(String id,int value){Spinner<Integer> spinner=(Spinner<Integer>)nodes(Spinner.class).stream().filter(s->id.equals(s.getId())).findFirst().orElseThrow();spinner.getValueFactory().setValue(value);}
    private static String labelText(){return nodes(Label.class).stream().map(Label::getText).reduce("",(a,b)->a+"\n"+b);}
    private static boolean progress(){return nodes(ProgressIndicator.class).stream().anyMatch(Node::isVisible);}
    private static <T extends Node> List<T> nodes(Class<T> type){List<T> found=new ArrayList<>();walk(view,type,found,Collections.newSetFromMap(new IdentityHashMap<>()));return found;}
    private static <T extends Node> void walk(Node node,Class<T> type,List<T> found,Set<Node> seen){if(node==null||!seen.add(node))return;if(type.isInstance(node))found.add(type.cast(node));if(node instanceof ScrollPane scroll)walk(scroll.getContent(),type,found,seen);if(node instanceof TitledPane titled)walk(titled.getContent(),type,found,seen);if(node instanceof Parent parent)for(Node child:parent.getChildrenUnmodifiable())walk(child,type,found,seen);}
    private static <T> T fx(Callable<T> action) throws Exception {if(Platform.isFxApplicationThread())return action.call();FutureTask<T> task=new FutureTask<>(action);Platform.runLater(task);return task.get(5,TimeUnit.SECONDS);}
    private static void await(BooleanSupplier condition,String description) throws Exception {
        CompletableFuture<Void> ready=new CompletableFuture<>();long until=Math.min(deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(8));
        fx(()->{new AnimationTimer(){@Override public void handle(long now){try{if(fixtureFailure.get()!=null)throw new AssertionError("HTTP fixture failed",fixtureFailure.get());if(condition.getAsBoolean()){stop();ready.complete(null);}else if(System.nanoTime()>until){stop();ready.completeExceptionally(new AssertionError("Timed out waiting for "+description+"; "+labelText()));}}catch(Throwable error){stop();ready.completeExceptionally(error);}}}.start();return null;});
        ready.get(9,TimeUnit.SECONDS);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    private static void respond(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();
        String input=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),output="[]";int status=200;
        try {
            if(path.equals("/api/auth/login")){check(method.equals("POST"),"Login method");output="{\"token\":\"fixture-only-teacher-token\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"user\":{\"id\":\""+TEACHER+"\",\"email\":\"teacher@fixture.invalid\",\"role\":\"TEACHER\"}}";}
            else {
                check("Bearer fixture-only-teacher-token".equals(exchange.getRequestHeaders().getFirst("Authorization")),"Missing teacher session: "+path);
                if(path.equals("/api/classes")&&method.equals("POST")){check(input.contains("\"name\":\"Fixture class\""),"Class name request");classCreated.set(true);output=group(true);}
                else if(path.equals("/api/classes")&&method.equals("GET"))output=classCreated.get()?"["+group(false)+(assignmentMode.get()?","+group(false).replace(CLASS,CLASS_SECOND).replace("Fixture class","Fixture second class"):"")+"]":"[]";
                else if(path.equals("/api/classes/"+CLASS+"/members")&&method.equals("GET")){memberReads.incrementAndGet();output="["+member(OLD,"first-class@fixture.invalid")+"]";}
                else if(path.equals("/api/classes/"+CLASS_SECOND+"/members")&&method.equals("GET")){memberReads.incrementAndGet();output="["+member(LATEST,"selected-recipient@fixture.invalid")+","+member(OTHER_STUDENT,"unselected-recipient@fixture.invalid")+"]";}
                else if(path.equals("/api/v2/teacher/banks")&&method.equals("POST")){check(input.contains("\"name\":\"Fixture second bank\""),"Bank name request");bankCreated.set(true);output=bank(BANK_B,"Fixture second bank");}
                else if(path.equals("/api/v2/teacher/banks")&&method.equals("GET"))output="["+bank(BANK_A,"Fixture first bank")+(bankCreated.get()?","+bank(BANK_B,"Fixture second bank"):"")+"]";
                else if(path.equals("/api/v2/teacher/questions")&&method.equals("GET")){questionQuery.set(exchange.getRequestURI().getQuery());output=("bankId="+BANK_B).equals(questionQuery.get())?"[]":"["+question(LATEST,2,"DRAFT","Latest draft fixture")+"]";}
                else if(path.equals("/api/v2/teacher/questions/"+QUESTION+"/versions")&&method.equals("GET")){historyReads.incrementAndGet();output="["+question(LATEST,2,"DRAFT","Latest draft fixture")+","+question(OLD,1,"APPROVED","Approved historical fixture")+"]";}
                else if(path.equals("/api/v2/teacher/quizzes")&&method.equals("POST")){check(input.contains("\"title\":\"Historical version quiz\"")&&input.contains(OLD),"Selected historical version request");quizBody.set(input);output="{\"id\":\""+CLASS+"\",\"title\":\"Historical version quiz\",\"status\":\"READY\",\"questionVersionIds\":[\""+OLD+"\"]}";}
                else if(path.equals("/api/v2/teacher/quizzes")&&method.equals("GET"))output=quizBody.get()==null?"[]":"[{\"id\":\""+CLASS+"\",\"title\":\"Historical version quiz\",\"status\":\"READY\",\"questionVersionIds\":[\""+OLD+"\"]}]";
                else if(path.equals("/api/v2/teacher/assignments")&&method.equals("POST")){
                    requireString(input,"title","Fixture assigned test");requireString(input,"classId",CLASS_SECOND);requireString(input,"quizId",CLASS);
                    requireString(input,"opensAt",utc("2098-01-01 09:00"));requireString(input,"closesAt",utc("2098-01-01 10:30"));
                    requireString(input,"scoreReleaseAt",utc("2098-01-01 10:35"));requireString(input,"answerReleaseAt",utc("2098-01-01 10:40"));
                    requireString(input,"answerReleaseMode","AFTER_VALID_SUBMISSION");
                    check(input.contains("\"durationMinutes\":35")&&input.contains("\"maxAttempts\":3"),"Assignment duration/max attempts request");
                    check(input.contains("\"studentIds\":[\""+LATEST+"\"]")&&!input.contains(OTHER_STUDENT)&&!input.contains(OLD),"Only explicitly selected current-class recipient must be posted");
                    assignmentBody.set(input);output=assignment();
                }
                else if(path.equals("/api/v2/teacher/assignments")&&method.equals("GET"))output=assignmentBody.get()==null?"[]":"["+assignment()+"]";
                else if(path.equals("/api/v2/teacher/assignments/"+ASSIGNMENT+"/publish")&&method.equals("POST")){
                    check(assignmentBody.get()!=null,"Cannot publish before create");check(input.isBlank(),"Publish should use existing assignment, without replacement client content");published.set(true);output=assignment();
                }
                else if(path.equals("/api/v2/teacher/assignments/"+ASSIGNMENT+"/results")&&method.equals("GET")){
                    check(published.get(),"Results must be read after fixture publish");resultReads.incrementAndGet();
                    output="[{\"studentId\":\""+LATEST+"\",\"email\":\"selected-recipient@fixture.invalid\",\"attemptsUsed\":1,\"bestScore\":0.75,\"attempts\":[{\"id\":\""+QUESTION+"\",\"attemptNo\":1,\"status\":\"SUBMITTED\",\"startedAt\":\""+NOW+"\",\"deadlineAt\":\""+NOW+"\",\"submittedAt\":\""+NOW+"\",\"earnedPoints\":3,\"possiblePoints\":4,\"normalizedScore\":0.75}]}]";
                }
                else if(path.equals("/api/classes/"+CLASS+"/study-summary")&&method.equals("GET")){summaryReads.incrementAndGet();output="[{\"studentId\":\""+OLD+"\",\"email\":\"summary-student@fixture.invalid\",\"completedSessions\":2,\"totalQuestions\":4,\"correctAnswers\":3,\"accuracy\":0.75},{\"studentId\":\""+LATEST+"\",\"email\":\"no-activity@fixture.invalid\",\"completedSessions\":0,\"totalQuestions\":0,\"correctAnswers\":0,\"accuracy\":null}]";}
                else if(!method.equals("GET")||!Set.of("/api/catalog/subjects","/api/v2/documents").contains(path))throw new AssertionError("Unexpected fixture request: "+method+" "+path);
            }
        } catch(Throwable failure){fixtureFailure.compareAndSet(null,failure);status=500;output="{}";}
        byte[] data=output.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,data.length);exchange.getResponseBody().write(data);exchange.close();
    }
    private static String group(boolean code){return "{\"id\":\""+CLASS+"\",\"teacherId\":\""+TEACHER+"\",\"name\":\"Fixture class\",\"status\":\"ACTIVE\",\"joinEnabled\":true,\"joinCode\":"+(code?"\"FIXTURE-CODE\"":"null")+"}";}
    private static String member(String id,String email){return "{\"membershipId\":\""+id+"\",\"studentId\":\""+id+"\",\"email\":\""+email+"\",\"joinedAt\":\""+NOW+"\"}";}
    private static String utc(String local){return LocalDateTime.parse(local.replace(' ','T')).atZone(ZoneId.systemDefault()).toInstant().toString();}
    private static void requireString(String body,String key,String value){check(body.contains("\""+key+"\":\""+value+"\""),"Assignment request field mismatch: "+key);}
    private static String assignment(){return "{\"id\":\""+ASSIGNMENT+"\",\"classId\":\""+CLASS_SECOND+"\",\"quizId\":\""+CLASS+"\",\"title\":\"Fixture assigned test\",\"opensAt\":\""+utc("2098-01-01 09:00")+"\",\"closesAt\":\""+utc("2098-01-01 10:30")+"\",\"durationMinutes\":35,\"maxAttempts\":3,\"status\":\""+(published.get()?"PUBLISHED":"DRAFT")+"\",\"scoreReleaseAt\":\""+utc("2098-01-01 10:35")+"\",\"answerReleaseAt\":\""+utc("2098-01-01 10:40")+"\",\"answerReleaseMode\":\"AFTER_VALID_SUBMISSION\"}";}
    private static String bank(String id,String name){return "{\"id\":\""+id+"\",\"name\":\""+name+"\",\"archivedAt\":null,\"createdAt\":\""+NOW+"\"}";}
    private static String question(String version,int number,String approval,String stem){return "{\"questionId\":\""+QUESTION+"\",\"versionId\":\""+version+"\",\"bankId\":\""+BANK_A+"\",\"versionNo\":"+number+",\"stem\":\""+stem+"\",\"subjectId\":null,\"topicId\":null,\"difficulty\":\"MEDIUM\",\"approvalStatus\":\""+approval+"\",\"origin\":\"MANUAL\",\"options\":[{\"id\":\""+OLD+"\",\"position\":1,\"text\":\"First\",\"correct\":true},{\"id\":\""+LATEST+"\",\"position\":2,\"text\":\"Second\",\"correct\":false}]}";}
}
