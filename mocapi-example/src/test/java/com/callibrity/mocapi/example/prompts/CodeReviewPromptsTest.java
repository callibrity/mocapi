package com.callibrity.mocapi.example.prompts;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeReviewPromptsTest {
    @Test
    void returnsCodeReviewPrompt() {
        var targetObject = new CodeReviewPrompts();
        var result = targetObject.reviewCode("python", "def hello_world():\n    print('Hello, world!')");
        assertThat(result).isNotNull();
        assertThat(result.messages()).hasSize(1);
    }
}