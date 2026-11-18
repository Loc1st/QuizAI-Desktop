package com.quizai.server.v2.ai;

import com.quizai.server.web.ApiException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McqValidatorTest {
    @Test void acceptsStrictMcqWithExactlyOneCorrectOption() {
        var result = McqValidator.parse("{\"questions\":[{\"stem\":\"Java?\",\"options\":[{\"text\":\"class\",\"correct\":true},{\"text\":\"function\",\"correct\":false}]}]}", 1);
        assertEquals("Java?", result.getFirst().stem());
        assertTrue(result.getFirst().options().getFirst().correct());
    }

    @Test void rejectsZeroTwoCorrectUnknownFieldsWrongTypesAndCount() {
        String[] invalid = {
            "{\"questions\":[{\"stem\":\"Q\",\"options\":[{\"text\":\"A\",\"correct\":false},{\"text\":\"B\",\"correct\":false}]}]}",
            "{\"questions\":[{\"stem\":\"Q\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":true}]}]}",
            "{\"questions\":[{\"stem\":\"Q\",\"options\":[{\"text\":\"A\",\"correct\":\"true\"},{\"text\":\"B\",\"correct\":false}]}]}",
            "{\"questions\":[],\"secret\":1}",
            "```json\n{\"questions\":[]}\n```"
        };
        for (var value : invalid) assertEquals(502, assertThrows(ApiException.class, () -> McqValidator.parse(value, 1)).status());
    }

    @Test void rejectsAmbiguousDuplicateFieldsAndTrailingJsonValues() {
        String question = "{\"questions\":[{\"stem\":\"Q\",\"options\":[{\"text\":\"A\",\"correct\":true},{\"text\":\"B\",\"correct\":false}]}]}";
        assertThrows(ApiException.class, () -> McqValidator.parse(question + " {}", 1));
        assertThrows(ApiException.class, () -> McqValidator.parse(question.replace("\"stem\":\"Q\"", "\"stem\":\"old\",\"stem\":\"Q\""), 1));
    }
}
