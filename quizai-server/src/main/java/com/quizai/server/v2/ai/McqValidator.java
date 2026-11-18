package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

/** Never trusts schema-constrained provider output without local semantic validation. */
final class McqValidator {
    record Option(String text, boolean correct) { }
    record Question(String stem, List<Option> options) { }
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    static List<Question> parse(String value, int expectedCount) {
        try {
            if (value == null || value.length() > 100_000 || expectedCount < 1 || expectedCount > 20) throw invalid();
            JsonNode root = JSON.readTree(value);
            fields(root, Set.of("questions"));
            JsonNode questions = root.path("questions");
            if (!questions.isArray() || questions.size() != expectedCount) throw invalid();
            var result = new ArrayList<Question>();
            var stems = new HashSet<String>();
            for (JsonNode question : questions) {
                fields(question, Set.of("stem", "options"));
                String stem = text(question.path("stem"), 4000);
                if (!stems.add(stem.toLowerCase(Locale.ROOT))) throw invalid();
                JsonNode options = question.path("options");
                if (!options.isArray() || options.size() < 2 || options.size() > 6) throw invalid();
                var parsedOptions = new ArrayList<Option>();
                var optionTexts = new HashSet<String>();
                int correct = 0;
                for (JsonNode option : options) {
                    fields(option, Set.of("text", "correct"));
                    String label = text(option.path("text"), 2000);
                    if (!optionTexts.add(label.toLowerCase(Locale.ROOT)) || !option.path("correct").isBoolean()) throw invalid();
                    boolean isCorrect = option.path("correct").booleanValue();
                    if (isCorrect) correct++;
                    parsedOptions.add(new Option(label, isCorrect));
                }
                if (correct != 1) throw invalid();
                result.add(new Question(stem, List.copyOf(parsedOptions)));
            }
            return List.copyOf(result);
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw invalid(); }
    }

    private static void fields(JsonNode node, Set<String> expected) {
        if (!node.isObject() || !Set.copyOf(node.propertyNames()).equals(expected)) throw invalid();
    }
    private static String text(JsonNode node, int max) {
        if (!node.isTextual() || node.textValue().isBlank() || node.textValue().length() > max
                || node.textValue().chars().anyMatch(c -> c < 32 && c != '\n' && c != '\t')) throw invalid();
        return node.textValue().strip();
    }
    private static ApiException invalid() { return new ApiException(502, "AI returned invalid MCQ data; retry explicitly"); }
}
