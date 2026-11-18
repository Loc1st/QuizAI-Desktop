package com.quizai.admin.ui.controllers;

import com.quizai.admin.ui.navigation.Screen;
import com.quizai.common.api.ApiModels.*;
import com.quizai.common.api.QuizApiClient;
import com.quizai.common.api.V2ApiClient;
import com.quizai.common.api.StudyModels.Document;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javafx.stage.FileChooser;
import java.io.File;

/** Teacher workflows consume REST; all results are rendered only after a completed request. */
public final class TeacherWorkspaceController extends AsyncController {
    private V2ApiClient v2;
    private Screen screen;
    private List<Bank> banks=List.of();
    private List<Question> questions=List.of();
    private List<Quiz> quizzes=List.of();
    private List<ClassView> classes=List.of();
    private List<Assignment> assignments=List.of();
    private List<CatalogItem> subjects=List.of();
    private List<Document> documents=List.of();
    private final Map<UUID,List<CatalogItem>> topics=new ConcurrentHashMap<>();
    private final Map<UUID,Question> selectedVersions=new LinkedHashMap<>();
    private final Map<UUID,List<CheckBox>> versionSelections=new HashMap<>();
    private VBox selectionSummary;
    private UUID selectedBankId;
    private UUID selectedSubjectId;
    private UUID selectedTopicId;
    private String selectedDifficulty;
    private static final DateTimeFormatter DISPLAY=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    public void configure(QuizApiClient api, Runnable expired, Screen screen, Consumer<Screen> navigate) {
        configureApi(api,expired); v2=api.v2(); this.screen=screen;
        workspace.getChildren().setAll(label(screen.title(),"page-title"),button("Thử tải lại",()->{
            if(screen==Screen.CLASSES)loadClasses();
            else if(screen==Screen.BANK||screen==Screen.REVIEW)loadBank();
            else if(screen==Screen.SUMMARY)loadSummary();
            else if(screen==Screen.ASSIGN||screen==Screen.RESULTS)loadAssignments();
        }));
        if(screen==Screen.OVERVIEW) {
            workspace.getChildren().setAll(label("Không gian giảng dạy", "page-title"),
                    label(api.currentUser().email(), "muted"),
                    card(label("Chuẩn bị và giao bài", "section-title"),label("Tạo lớp, duyệt câu hỏi và tạo bộ đề trước khi giao cho sinh viên.","muted"),
                            row(button("Quản lý lớp",()->navigate.accept(Screen.CLASSES)),button("Ngân hàng và bộ đề",()->navigate.accept(Screen.BANK)),button("Giao bài",()->navigate.accept(Screen.ASSIGN)))),
                    card(label("Kết quả từ máy chủ", "section-title"),button("Xem kết quả bài giao",()->navigate.accept(Screen.RESULTS))));
        } else if(screen==Screen.CLASSES) loadClasses();
        else if(screen==Screen.BANK||screen==Screen.REVIEW) loadBank();
        else if(screen==Screen.SUMMARY) loadSummary();
        else if(screen==Screen.ASSIGN||screen==Screen.RESULTS) loadAssignments();
        else workspace.getChildren().setAll(label(screen.title(),"page-title"),label(screen==Screen.LATE?"Duyệt bài muộn được hoãn theo CR-001. Bài thi hiện tại chỉ nhận câu trả lời đã được máy chủ xác nhận đúng hạn.":"Thống kê tự học chưa được kết nối trong bước triển khai này.","muted"));
    }

    private void loadClasses() { run(api::listClasses, values->{classes=values;renderClasses();}); }
    private void renderClasses() {
        TextField name=field("Tên lớp"); name.setId("teacher-class-name");
        workspace.getChildren().setAll(label("Lớp của bạn", "page-title"),
                card(row(name,button("Tạo lớp",()->{
                    if(name.getText().isBlank()){feedback.setText("Nhập tên lớp.");return;}
                    run(()->api.createClass(name.getText().trim()),value->{classes=new ArrayList<>(classes);classes.add(value);renderClasses();feedback.setText("Đã tạo lớp. Mã tham gia: "+value.joinCode());});
                }),button("Làm mới",this::loadClasses))));
        if(classes.isEmpty()) workspace.getChildren().add(label("Chưa có lớp. Tạo lớp để lấy mã tham gia cho sinh viên.","muted"));
        for(ClassView item:classes) {
            Label code=label(item.joinCode()==null?"Mã hiện tại được giữ kín; đổi mã để lấy mã mới.":"Mã tham gia: "+item.joinCode(),"small-heading");
            TextField rename=field("Tên lớp mới"); rename.setText(item.name());
            VBox members=new VBox(8);
            VBox box=card(label(item.name(),"section-title"),code,
                    row(rename,button("Đổi tên",()->run(()->api.updateClass(item.id(),rename.getText().trim(),"ACTIVE"),v->loadClasses()))),
                    row(button(item.joinEnabled()?"Tắt mã tham gia":"Bật mã tham gia",()->run(()->api.setJoinEnabled(item.id(),!item.joinEnabled()),this::replaceClass)),
                            button("Đổi mã",()->run(()->api.rotateJoinCode(item.id()),this::replaceClass)),
                            button("Thành viên",()->run(()->api.listMembers(item.id()),values->{members.getChildren().clear(); if(values.isEmpty())members.getChildren().add(label("Chưa có thành viên đang tham gia.","muted"));for(Member m:values)members.getChildren().add(label(m.email()+" · "+date(m.joinedAt()),"muted"));}))),members);
            workspace.getChildren().add(box);
        }
    }
    private void replaceClass(ClassView updated){classes=classes.stream().map(value->value.id().equals(updated.id())?updated:value).toList();renderClasses();feedback.setText(updated.joinCode()==null?"Đã cập nhật lớp.":"Mã tham gia mới: "+updated.joinCode());}

    private void loadBank() { loadBank(null); }
    private void loadBank(String successMessage) {
        run(()->v2.subjects().thenCompose(s->{subjects=s;topics.clear();return CompletableFuture.allOf(s.stream().map(item->v2.topics(item.id()).thenAccept(values->topics.put(item.id(),values))).toArray(CompletableFuture[]::new));})
                .thenCompose(ignored->api.study().documents()).thenCompose(d->{documents=d;return v2.banks();}).thenCompose(b->{banks=b;return v2.questions(selectedBankId);}).thenCompose(q->{questions=q;return v2.quizzes();}),values->{quizzes=values;renderBank();if(successMessage!=null)feedback.setText(successMessage);});
    }
    private void renderBank() {
        versionSelections.clear();
        TextField bankName=field("Tên ngân hàng mới");bankName.setId("teacher-bank-name");
        ComboBox<Bank> bankFilter=combo(banks,Bank::name); bankFilter.setPromptText("Tất cả ngân hàng");bankFilter.setId("teacher-bank-filter");
        banks.stream().filter(b->b.id().equals(selectedBankId)).findFirst().ifPresent(bankFilter::setValue);
        bankFilter.setOnAction(e->{selectedBankId=bankFilter.getValue()==null?null:bankFilter.getValue().id();loadBank();});
        ComboBox<CatalogItem> subjectFilter=combo(subjects,CatalogItem::name);subjectFilter.setPromptText("Tất cả môn học");
        subjects.stream().filter(s->s.id().equals(selectedSubjectId)).findFirst().ifPresent(subjectFilter::setValue);
        ComboBox<CatalogItem> topicFilter=combo(selectedSubjectId==null?List.of():topics.getOrDefault(selectedSubjectId,List.of()),CatalogItem::name);topicFilter.setPromptText("Tất cả chủ đề");
        topicFilter.getItems().stream().filter(t->t.id().equals(selectedTopicId)).findFirst().ifPresent(topicFilter::setValue);
        ComboBox<String> difficultyFilter=combo(List.of("EASY","MEDIUM","HARD"),Function.identity());difficultyFilter.setPromptText("Tất cả độ khó");difficultyFilter.setValue(selectedDifficulty);
        subjectFilter.setOnAction(e->{selectedSubjectId=subjectFilter.getValue()==null?null:subjectFilter.getValue().id();selectedTopicId=null;renderBank();});
        topicFilter.setOnAction(e->{selectedTopicId=topicFilter.getValue()==null?null:topicFilter.getValue().id();renderBank();});
        difficultyFilter.setOnAction(e->{selectedDifficulty=difficultyFilter.getValue();renderBank();});
        workspace.getChildren().setAll(label("Ngân hàng câu hỏi và bộ đề", "page-title"),
                card(row(bankName,button("Tạo ngân hàng",()->{
                    if(bankName.getText().isBlank()){feedback.setText("Nhập tên ngân hàng.");return;}
                    run(()->v2.createBank(bankName.getText().trim()),b->{selectedBankId=b.id();loadBank("Đã tạo ngân hàng: "+b.name());});
                })),row(bankFilter,button("Tất cả ngân hàng",()->{selectedBankId=null;loadBank();}),button("Làm mới",this::loadBank)),
                        row(subjectFilter,topicFilter,difficultyFilter,button("Xóa bộ lọc phân loại",()->{selectedSubjectId=null;selectedTopicId=null;selectedDifficulty=null;renderBank();}))),
                generationForm(),catalogForm(),questionForm(null));
        List<Question> visibleQuestions=questions.stream().filter(q->selectedSubjectId==null||selectedSubjectId.equals(q.subjectId()))
                .filter(q->selectedTopicId==null||selectedTopicId.equals(q.topicId())).filter(q->selectedDifficulty==null||selectedDifficulty.equals(q.difficulty())).toList();
        if(visibleQuestions.isEmpty())workspace.getChildren().add(label("Chưa có câu hỏi trong phạm vi đã chọn.","muted"));
        if(banks.isEmpty())workspace.getChildren().add(label("Tạo ngân hàng trước khi thêm câu hỏi hoặc tạo câu hỏi AI.","muted"));
        for(Question q:visibleQuestions) {
            CheckBox select=versionSelection(q);
            VBox box=card(label(q.stem(),"section-title"),label("Phiên bản "+q.versionNo()+" · "+q.approvalStatus()+" · "+q.difficulty()+" · "+q.origin(),"caption"));
            for(QuestionOption o:q.options())box.getChildren().add(label(o.position()+". "+o.text()+(o.correct()?" ✓":""),"muted"));
            VBox editor=new VBox(8),history=new VBox(8);
            HBox actions=row(select,button("Sửa thành phiên bản mới",()->{
                editor.getChildren().setAll(questionForm(q));
            }),button("Lịch sử phiên bản",()->run(()->v2.versions(q.questionId()),versions->renderVersions(history,versions))));
            if(!"APPROVED".equals(q.approvalStatus()))actions.getChildren().add(button("Duyệt câu hỏi",()->run(()->v2.approve(q),v->loadBank("Đã duyệt phiên bản "+v.versionNo()+"."))));
            box.getChildren().addAll(actions,history,editor); workspace.getChildren().add(box);
        }
        selectionSummary=new VBox(8);renderSelectionSummary();
        TextField quizTitle=field("Tên bộ đề");
        workspace.getChildren().add(card(label("Tạo bộ đề từ các phiên bản đã duyệt được chọn", "section-title"),selectionSummary,row(quizTitle,button("Tạo bộ đề",()->{
            if(quizTitle.getText().isBlank()||selectedVersions.isEmpty()){feedback.setText("Nhập tên và chọn ít nhất một câu hỏi đã duyệt.");return;}
            if(selectedVersions.size()>200){feedback.setText("Mỗi bộ đề tối đa 200 phiên bản câu hỏi.");return;}
            run(()->v2.createQuiz(quizTitle.getText().trim(),List.copyOf(selectedVersions.keySet())),q->{selectedVersions.clear();loadBank("Đã tạo bộ đề: "+q.title());});
        }))));
        for(Quiz q:quizzes)workspace.getChildren().add(card(label(q.title(),"section-title"),label(q.status()+" · "+q.questionVersionIds().size()+" câu", "caption"),
                button("Thay danh sách câu bằng các phiên bản đang chọn",()->{
                    if(selectedVersions.isEmpty()){feedback.setText("Chọn ít nhất một phiên bản đã duyệt ở trên.");return;}
                    if(selectedVersions.size()>200){feedback.setText("Mỗi bộ đề tối đa 200 phiên bản câu hỏi.");return;}
                    run(()->v2.updateQuiz(q.id(),q.title(),List.copyOf(selectedVersions.keySet())),value->{selectedVersions.clear();loadBank("Đã cập nhật danh sách câu của bộ đề: "+value.title());});
                })));
    }
    private CheckBox versionSelection(Question question) {
        boolean approved="APPROVED".equals(question.approvalStatus());
        CheckBox select=new CheckBox(approved?"Chọn cho bộ đề":"Cần duyệt trước khi chọn");
        select.setId("teacher-select-version-"+question.versionId());
        select.setSelected(selectedVersions.containsKey(question.versionId()));select.setDisable(!approved);
        if(!approved)select.setTooltip(new Tooltip("Chỉ phiên bản APPROVED mới được dùng cho bộ đề giảng viên."));
        versionSelections.computeIfAbsent(question.versionId(),ignored->new ArrayList<>()).add(select);
        select.setOnAction(e->{if(select.isSelected())selectedVersions.put(question.versionId(),question);else selectedVersions.remove(question.versionId());syncVersionSelection(question.versionId());renderSelectionSummary();});
        return select;
    }
    private void syncVersionSelection(UUID versionId) {
        versionSelections.getOrDefault(versionId,List.of()).forEach(choice->choice.setSelected(selectedVersions.containsKey(versionId)));
    }
    private void renderSelectionSummary() {
        if(selectionSummary==null)return;
        selectionSummary.getChildren().setAll(label("Đã chọn "+selectedVersions.size()+" phiên bản. Lựa chọn được giữ khi đổi ngân hàng hoặc tạo phiên bản mới.","muted"));
        for(Question selected:selectedVersions.values())selectionSummary.getChildren().add(row(label("v"+selected.versionNo()+" · "+selected.stem(),"caption"),button("Bỏ chọn",()->{selectedVersions.remove(selected.versionId());syncVersionSelection(selected.versionId());renderSelectionSummary();})));
    }
    private void renderVersions(VBox host,List<Question> versions) {
        host.getChildren().setAll(label("Lịch sử phiên bản · chọn chính xác phiên bản dùng cho bộ đề", "small-heading"));
        if(versions.isEmpty())host.getChildren().add(label("Chưa có phiên bản.","muted"));
        for(Question version:versions) {
            VBox item=card(label("Phiên bản "+version.versionNo()+" · "+version.approvalStatus(),"caption"),label(version.stem(),"muted"));
            for(QuestionOption option:version.options())item.getChildren().add(label(option.position()+". "+option.text()+(option.correct()?" ✓":""),"muted"));
            item.getChildren().add(versionSelection(version));
            if(!"APPROVED".equals(version.approvalStatus()))item.getChildren().add(button("Duyệt phiên bản "+version.versionNo(),()->run(()->v2.approve(version),approved->loadBank("Đã duyệt phiên bản "+approved.versionNo()+"."))));
            host.getChildren().add(item);
        }
        host.getChildren().add(button("Ẩn lịch sử",()->host.getChildren().clear()));
    }
    private TitledPane generationForm(){
        TextField title=field("Tên tài liệu / bộ đề AI");TextArea pasted=new TextArea();pasted.setPromptText("Dán văn bản để tạo câu hỏi");pasted.setPrefRowCount(4);pasted.setWrapText(true);
        ComboBox<Document> source=combo(documents,d->d.fileName()==null?"Văn bản · "+date(d.createdAt()):d.fileName());source.setPromptText("Tài liệu của bạn");
        ComboBox<Bank> bank=combo(banks,Bank::name);bank.setPromptText("Ngân hàng nhận câu hỏi");Spinner<Integer> count=new Spinner<>(1,20,5);
        ComboBox<String> difficulty=new ComboBox<>();difficulty.getItems().setAll("EASY","MEDIUM","HARD");difficulty.setValue("MEDIUM");
        VBox body=card(title,pasted,row(button("Lưu văn bản",()->{
            if(title.getText().isBlank()||pasted.getText().isBlank()){feedback.setText("Nhập tên và nội dung tài liệu.");return;}
            run(()->api.study().paste(title.getText().trim(),pasted.getText()),d->loadBank("Đã lưu tài liệu văn bản. Chọn tài liệu trong danh sách để tạo câu hỏi."));
        }),button("Nhập PDF / DOCX / TXT",()->{
            FileChooser chooser=new FileChooser();chooser.setTitle("Chọn tài liệu (tối đa 5 MB)");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF, DOCX, TXT","*.pdf","*.docx","*.txt"));
            File file=chooser.showOpenDialog(workspace.getScene().getWindow());if(file!=null)run(()->api.study().upload(file.toPath()),d->loadBank("Đã nhập tài liệu: "+file.getName()));
        })),source,row(bank,label("Số câu", "caption"),count,difficulty),
                label("Câu AI được lưu nháp. Duyệt từng phiên bản trước khi chọn cho bộ đề. Phiên bản đã duyệt cũ vẫn có thể chọn trong lịch sử.","muted"),button("Tạo câu hỏi AI",()->{
            if(title.getText().isBlank()||source.getValue()==null||bank.getValue()==null){feedback.setText("Nhập tên bộ đề, chọn tài liệu và ngân hàng.");return;}
            run(()->api.study().generate(Map.of("documentId",source.getValue().id(),"title",title.getText().trim(),"count",count.getValue(),"bankId",bank.getValue().id(),"difficulty",difficulty.getValue())),generated->{selectedBankId=bank.getValue().id();loadBank("Đã tạo câu hỏi AI dạng nháp. Rà soát và duyệt trước khi giao.");});
        }));
        TitledPane pane=new TitledPane("Tạo câu hỏi AI từ tài liệu",body);pane.setExpanded(screen==Screen.REVIEW);return pane;
    }

    private VBox questionForm(Question previous) {
        ComboBox<Bank> bank=combo(banks,Bank::name);bank.setPromptText("Ngân hàng");
        if(previous!=null)banks.stream().filter(b->b.id().equals(previous.bankId())).findFirst().ifPresent(bank::setValue);
        else banks.stream().filter(b->b.id().equals(selectedBankId)).findFirst().ifPresent(bank::setValue);
        if(previous!=null){bank.setDisable(true);bank.setTooltip(new Tooltip("Phiên bản mới giữ ngân hàng của câu hỏi gốc."));}
        TextArea stem=new TextArea(previous==null?"":previous.stem());stem.setPromptText("Nội dung câu hỏi trắc nghiệm");stem.setPrefRowCount(3);stem.setWrapText(true);
        ComboBox<String> difficulty=new ComboBox<>();difficulty.getItems().setAll("EASY","MEDIUM","HARD");difficulty.setValue(previous==null?"MEDIUM":previous.difficulty());
        ComboBox<CatalogItem> subject=combo(subjects,CatalogItem::name);subject.setPromptText("Môn học (tùy chọn)");
        ComboBox<CatalogItem> topic=combo(List.of(),CatalogItem::name);topic.setPromptText("Chủ đề (tùy chọn)");
        subject.setOnAction(e->{topic.setValue(null);topic.getItems().setAll(subject.getValue()==null?List.of():topics.getOrDefault(subject.getValue().id(),List.of()));});
        if(previous!=null){subjects.stream().filter(s->s.id().equals(previous.subjectId())).findFirst().ifPresent(subject::setValue);topic.getItems().stream().filter(t->t.id().equals(previous.topicId())).findFirst().ifPresent(topic::setValue);}
        VBox optionRows=new VBox(8);ToggleGroup correct=new ToggleGroup();List<TextField> optionTexts=new ArrayList<>();List<RadioButton> correctButtons=new ArrayList<>();
        List<Button> removeButtons=new ArrayList<>();
        Button add=button("Thêm lựa chọn",()->{});
        Runnable updateOptionControls=()->{add.setDisable(optionTexts.size()>=10);removeButtons.forEach(remove->remove.setDisable(optionTexts.size()<=2));};
        Runnable addOption=()->{
            if(optionTexts.size()>=10)return;
            TextField text=field("Nội dung lựa chọn");RadioButton radio=new RadioButton("Đúng");radio.setToggleGroup(correct);
            Button remove=button("Xóa lựa chọn",()->{});remove.setTooltip(new Tooltip("Câu hỏi phải còn ít nhất 2 lựa chọn."));
            HBox optionRow=row(text,radio,remove);
            remove.setOnAction(e->{if(optionTexts.size()<=2)return;radio.setToggleGroup(null);optionTexts.remove(text);correctButtons.remove(radio);removeButtons.remove(remove);optionRows.getChildren().remove(optionRow);updateOptionControls.run();});
            optionTexts.add(text);correctButtons.add(radio);removeButtons.add(remove);optionRows.getChildren().add(optionRow);updateOptionControls.run();
        };
        add.setOnAction(e->addOption.run());add.setTooltip(new Tooltip("Tối đa 10 lựa chọn cho mỗi câu hỏi."));
        int count=previous==null?4:previous.options().size();for(int i=0;i<count;i++){addOption.run();if(previous!=null){optionTexts.get(i).setText(previous.options().get(i).text());correctButtons.get(i).setSelected(previous.options().get(i).correct());}}
        return card(label(previous==null?"Thêm câu hỏi thủ công":"Tạo phiên bản mới (giữ nguyên đề đã giao)","section-title"),bank,stem,row(difficulty,subject,topic),
                row(button("Bỏ môn / chủ đề",()->{subject.setValue(null);topic.setValue(null);}),button("Bỏ chủ đề",()->topic.setValue(null))),
                label("Từ 2 đến 10 lựa chọn, đúng một đáp án đúng. Nội dung câu tối đa 20.000 ký tự; mỗi lựa chọn tối đa 10.000 ký tự.","caption"),optionRows,
                row(add,button("Lưu bản nháp",()->{
                    if(stem.getText().isBlank()||correct.getSelectedToggle()==null||optionTexts.stream().anyMatch(t->t.getText().isBlank())){feedback.setText("Nhập câu hỏi, các lựa chọn và chọn đúng một đáp án.");return;}
                    if(optionTexts.size()<2||optionTexts.size()>10||stem.getText().trim().length()>20000||optionTexts.stream().anyMatch(t->t.getText().trim().length()>10000)){feedback.setText("Câu hỏi cần 2–10 lựa chọn, nội dung câu tối đa 20.000 ký tự và mỗi lựa chọn tối đa 10.000 ký tự.");return;}
                    try {
                        Map<String,Object> body=new LinkedHashMap<>();body.put("stem",stem.getText().trim());body.put("difficulty",difficulty.getValue());
                        if(previous==null){if(bank.getValue()==null){feedback.setText("Chọn ngân hàng trước khi lưu.");return;}body.put("bankId",bank.getValue().id());}
                        if(subject.getValue()!=null)body.put("subjectId",subject.getValue().id());
                        if(topic.getValue()!=null)body.put("topicId",topic.getValue().id());
                        List<Map<String,Object>> options=new ArrayList<>();for(int i=0;i<optionTexts.size();i++)options.add(Map.of("text",optionTexts.get(i).getText().trim(),"correct",correctButtons.get(i).isSelected()));body.put("options",options);
                        run(()->previous==null?v2.createQuestion(body):v2.createVersion(previous.questionId(),body),q->loadBank("Đã lưu phiên bản "+q.versionNo()+" dạng nháp. Duyệt phiên bản trước khi chọn cho bộ đề."));
                    } catch(IllegalArgumentException error){feedback.setText("Dữ liệu câu hỏi chưa hợp lệ.");}
                })));
    }
    private TitledPane catalogForm(){
        TextField subjectCode=field("Mã môn"),subjectName=field("Tên môn"),topicCode=field("Mã chủ đề"),topicName=field("Tên chủ đề");
        ComboBox<CatalogItem> subject=combo(subjects,CatalogItem::name);subject.setPromptText("Môn của chủ đề");
        VBox catalog=new VBox(8);
        if(subjects.isEmpty())catalog.getChildren().add(label("Chưa có môn học. Thêm môn trước khi thêm chủ đề hoặc phân loại câu hỏi.","muted"));
        for(CatalogItem item:subjects){
            VBox entry=new VBox(5,label(item.code()+" · "+item.name(),"small-heading"));
            List<CatalogItem> children=topics.getOrDefault(item.id(),List.of());
            if(children.isEmpty())entry.getChildren().add(label("Chưa có chủ đề.","muted"));
            for(CatalogItem topic:children)entry.getChildren().add(label(topic.code()+" · "+topic.name(),"caption"));
            catalog.getChildren().add(entry);
        }
        TitledPane pane=new TitledPane("Danh mục môn học / chủ đề ("+subjects.size()+" môn)",card(catalog,row(subjectCode,subjectName,button("Thêm môn",()->{
            if(subjectCode.getText().isBlank()||subjectName.getText().isBlank()){feedback.setText("Nhập mã và tên môn.");return;}
            run(()->v2.createSubject(subjectCode.getText().trim(),subjectName.getText().trim()),v->loadBank("Đã thêm môn: "+v.name()));
        })),row(subject,topicCode,topicName,button("Thêm chủ đề",()->{
            if(subject.getValue()==null||topicCode.getText().isBlank()||topicName.getText().isBlank()){feedback.setText("Chọn môn, nhập mã và tên chủ đề.");return;}
            run(()->v2.createTopic(subject.getValue().id(),topicCode.getText().trim(),topicName.getText().trim()),v->loadBank("Đã thêm chủ đề: "+v.name()));
        }))));pane.setExpanded(false);return pane;
    }

    private void loadSummary() {
        run(api::listClasses,values->{
            classes=values;
            ComboBox<ClassView> group=combo(classes,ClassView::name);group.setPromptText("Chọn lớp để xem thống kê");group.setId("teacher-summary-class");
            VBox summaries=new VBox(12);summaries.setId("teacher-study-summary");
            Runnable refresh=()->{
                if(group.getValue()==null){feedback.setText("Chọn lớp để xem thống kê tự học.");return;}
                summaries.getChildren().setAll(label("Chưa có thống kê được tải cho lớp đã chọn.","muted"));
                run(()->v2.studySummary(group.getValue().id()),items->renderStudySummary(summaries,items));
            };
            Button refreshButton=button("Làm mới thống kê",refresh);refreshButton.setDisable(classes.isEmpty());
            if(classes.isEmpty())refreshButton.setTooltip(new Tooltip("Tạo lớp trước khi xem thống kê sinh viên."));
            group.setOnAction(e->refresh.run());
            workspace.getChildren().setAll(label("Thống kê tự học tổng hợp", "page-title"),
                    label("Chỉ tổng hợp các lượt luyện tập đã hoàn thành trong thời gian tham gia lớp của thành viên hiện tại. Không hiển thị tài liệu, câu hỏi, câu trả lời hoặc chat riêng của sinh viên.","muted"),
                    row(group,refreshButton),button("Làm mới danh sách lớp",this::loadSummary),summaries);
            if(classes.isEmpty())summaries.getChildren().setAll(label("Chưa có lớp. Tạo lớp và mời sinh viên tham gia để xem thống kê.","muted"));
            else summaries.getChildren().setAll(label("Chọn một lớp ở trên để tải thống kê từ máy chủ.","muted"));
        });
    }
    private void renderStudySummary(VBox host,List<StudentStudySummary> values) {
        host.getChildren().clear();
        if(values.isEmpty())host.getChildren().add(label("Lớp chưa có thành viên đang tham gia.","muted"));
        for(StudentStudySummary value:values){
            String accuracy=value.accuracy()==null?"Chưa có dữ liệu":value.accuracy().movePointRight(2).stripTrailingZeros().toPlainString()+"%";
            host.getChildren().add(card(label(value.email(),"section-title"),
                    label("Lượt luyện tập đã hoàn thành: "+value.completedSessions()+" · Tổng số câu: "+value.totalQuestions()+" · Trả lời đúng: "+value.correctAnswers(),"muted"),
                    label("Tỷ lệ đúng: "+accuracy,"caption")));
        }
    }

    private void loadAssignments() {
        run(()->api.listClasses().thenCompose(c->{classes=c;return v2.quizzes();}).thenCompose(q->{quizzes=q;return v2.assignments();}),a->{assignments=a;renderAssignments();});
    }
    private void renderAssignments() {
        workspace.getChildren().setAll(label(screen==Screen.ASSIGN?"Tạo và giao bài kiểm tra":"Kết quả bài giao", "page-title"),button("Làm mới",this::loadAssignments));
        if(screen==Screen.ASSIGN)workspace.getChildren().add(assignmentForm());
        if(assignments.isEmpty())workspace.getChildren().add(label("Chưa có bài giao.","muted"));
        for(Assignment a:assignments){
            VBox detail=new VBox(8);
            VBox box=card(label(a.title(),"section-title"),label(a.status()+" · "+date(a.opensAt())+" → "+date(a.closesAt())+" · "+a.durationMinutes()+" phút · "+a.maxAttempts()+" lượt","caption"),
                    label("Điểm: "+date(a.scoreReleaseAt())+" · Đáp án: "+date(a.answerReleaseAt())+" · "+a.answerReleaseMode(),"caption"));
            HBox actions=row(button("Xem kết quả",()->run(()->v2.results(a.id()),results->renderResults(detail,results))));
            if("DRAFT".equals(a.status()))actions.getChildren().add(button("Giao bài / đóng băng đề",()->run(()->v2.publish(a.id()),v->loadAssignments())));
            actions.getChildren().add(button("Công bố điểm / đáp án",()->detail.getChildren().setAll(releaseForm(a))));
            box.getChildren().addAll(actions,detail);workspace.getChildren().add(box);
        }
    }
    private VBox assignmentForm() {
        TextField title=field("Tên bài kiểm tra");ComboBox<ClassView> group=combo(classes,ClassView::name);group.setPromptText("Chọn lớp");
        ComboBox<Quiz> quiz=combo(quizzes,Quiz::title);quiz.setPromptText("Chọn bộ đề đã duyệt");
        title.setId("teacher-assignment-title");group.setId("teacher-assignment-class");quiz.setId("teacher-assignment-quiz");
        TextField opens=field("Mở: yyyy-MM-dd HH:mm");TextField closes=field("Đóng: yyyy-MM-dd HH:mm");
        opens.setText(LocalDateTime.now().plusMinutes(1).withSecond(0).withNano(0).toString().replace('T',' '));
        closes.setText(LocalDateTime.now().plusHours(1).withSecond(0).withNano(0).toString().replace('T',' '));
        Spinner<Integer> duration=new Spinner<>(1,1440,20);Spinner<Integer> maximum=new Spinner<>(1,100,1);
        duration.setId("teacher-assignment-duration");maximum.setId("teacher-assignment-max-attempts");
        TextField score=field("Công bố điểm: yyyy-MM-dd HH:mm (để trống: giữ kín)");TextField answers=field("Công bố đáp án: yyyy-MM-dd HH:mm (để trống: giữ kín)");
        ComboBox<String> mode=releaseMode("AFTER_ASSIGNMENT_CLOSE");
        mode.setId("teacher-assignment-release-mode");
        VBox recipients=new VBox(8);Map<UUID,CheckBox> selectedStudents=new LinkedHashMap<>();
        CheckBox all=new CheckBox("Giao cho tất cả thành viên hiện tại");all.setSelected(true);
        group.setOnAction(e->{if(group.getValue()!=null)run(()->api.listMembers(group.getValue().id()),members->{recipients.getChildren().clear();selectedStudents.clear();for(Member member:members){CheckBox choice=new CheckBox(member.email());selectedStudents.put(member.studentId(),choice);recipients.getChildren().add(choice);}recipients.setDisable(all.isSelected());});});
        all.setOnAction(e->recipients.setDisable(all.isSelected()));
        return card(label("Bài giao mới", "section-title"),title,row(group,quiz),row(label("Giờ địa phương: "+ZoneId.systemDefault(),"caption")),row(opens,closes),
                row(label("Phút / lượt", "caption"),duration,label("Số lượt tối đa", "caption"),maximum),score,answers,mode,all,recipients,
                label("Nếu công bố đáp án khi còn lượt làm, sinh viên có thể xem đáp án trước lượt tiếp theo.","caption"),button("Tạo bản nháp bài giao",()->{
                    if(title.getText().isBlank()||group.getValue()==null||quiz.getValue()==null){feedback.setText("Nhập tên, chọn lớp và bộ đề.");return;}
                    try {
                        Map<String,Object> body=new LinkedHashMap<>();body.put("title",title.getText().trim());body.put("classId",group.getValue().id());body.put("quizId",quiz.getValue().id());
                        body.put("opensAt",parseTime(opens.getText()));body.put("closesAt",parseTime(closes.getText()));body.put("durationMinutes",duration.getValue());body.put("maxAttempts",maximum.getValue());
                        body.put("scoreReleaseAt",optionalTime(score.getText()));body.put("answerReleaseAt",optionalTime(answers.getText()));body.put("answerReleaseMode",mode.getValue());
                        if(!all.isSelected()){List<UUID> ids=selectedStudents.entrySet().stream().filter(v->v.getValue().isSelected()).map(Map.Entry::getKey).toList();if(ids.isEmpty()){feedback.setText("Chọn ít nhất một sinh viên hoặc chọn giao cả lớp.");return;}body.put("studentIds",ids);}
                        run(()->v2.createAssignment(body),a->loadAssignments());
                    }catch(RuntimeException error){feedback.setText("Thời gian chưa hợp lệ. Dùng yyyy-MM-dd HH:mm theo giờ địa phương.");}
                }));
    }
    private VBox releaseForm(Assignment a) {
        TextField score=field("Công bố điểm: yyyy-MM-dd HH:mm");TextField answer=field("Công bố đáp án: yyyy-MM-dd HH:mm");
        if(a.scoreReleaseAt()!=null)score.setText(localInput(a.scoreReleaseAt()));if(a.answerReleaseAt()!=null)answer.setText(localInput(a.answerReleaseAt()));
        ComboBox<String> mode=releaseMode(a.answerReleaseMode());
        return card(label("Lịch công bố (giờ địa phương)","section-title"),score,answer,mode,button("Lưu lịch công bố",()->{
            try{Map<String,Object> body=new LinkedHashMap<>();body.put("scoreReleaseAt",optionalTime(score.getText()));body.put("answerReleaseAt",optionalTime(answer.getText()));body.put("answerReleaseMode",mode.getValue());run(()->v2.releases(a.id(),body),value->loadAssignments());}
            catch(RuntimeException error){feedback.setText("Thời gian chưa hợp lệ.");}
        }));
    }
    private void renderResults(VBox host,List<StudentResult> results) {
        host.getChildren().clear();if(results.isEmpty())host.getChildren().add(label("Chưa có người nhận bài.","muted"));
        for(StudentResult result:results){
            VBox box=card(label(result.email(),"small-heading"),label("Đã dùng "+result.attemptsUsed()+" lượt · Điểm cao nhất: "+score(result.bestScore()),"muted"));
            for(ResultAttempt a:result.attempts())box.getChildren().add(label("Lượt "+a.attemptNo()+" · "+a.status()+" · "+score(a.normalizedScore())+" · "+date(a.submittedAt()),"caption"));
            host.getChildren().add(box);
        }
    }
    private static ComboBox<String> releaseMode(String value){ComboBox<String> mode=new ComboBox<>();mode.getItems().setAll("AFTER_VALID_SUBMISSION","AFTER_ASSIGNMENT_CLOSE");mode.setValue(value);return mode;}
    private static <T> ComboBox<T> combo(List<T> values,Function<T,String> name){ComboBox<T> result=new ComboBox<>();result.getItems().setAll(values);result.setConverter(new StringConverter<>(){public String toString(T value){return value==null?"":name.apply(value);}public T fromString(String value){return null;}});return result;}
    private static String date(Instant value){return value==null?"Chưa công bố":DISPLAY.format(value);}
    private static String localInput(Instant value){return LocalDateTime.ofInstant(value,ZoneId.systemDefault()).withSecond(0).withNano(0).toString().replace('T',' ');}
    private static String parseTime(String value){return LocalDateTime.parse(value.trim().replace(' ','T')).atZone(ZoneId.systemDefault()).toInstant().toString();}
    private static String optionalTime(String value){return value.isBlank()?null:parseTime(value);}
    private static String score(BigDecimal value){return value==null?"Chưa có điểm":value.multiply(BigDecimal.TEN).stripTrailingZeros().toPlainString()+" / 10";}
}
