/*
 * Copyright © 2025 Callibrity, Inc. (contactus@callibrity.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.callibrity.mocapi.prompts.annotation;

import com.callibrity.mocapi.prompts.Role;
import com.callibrity.mocapi.prompts.content.TextContent;
import com.callibrity.ripcurl.core.exception.JsonRpcInternalErrorException;
import com.callibrity.ripcurl.core.exception.JsonRpcInvalidParamsException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotationMcpPromptTest {

    @Test
    void shouldExtractAllPromptAnnotatedMethods() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());
        assertThat(prompts).hasSize(5);
    }

    @Test
    void shouldExtractZeroParameters() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());
        var prompt = prompts.stream().filter(p -> "test-prompts.none".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        assertThat(prompt.name()).isEqualTo("test-prompts.none");
        assertThat(prompt.description()).isEqualTo("Test Prompts - None");
        assertThat(prompt.arguments()).isEmpty();
    }

    @Test
    void shouldCallWithZeroParameters() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.none".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        var result = prompt.getPrompt(new HashMap<>());
        assertThat(result.description()).isEqualTo("A test prompt");
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(Role.USER);
        assertThat(message.content()).isInstanceOf(TextContent.class);
    }

    @Test
    void shouldExtractSingleParameters() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());
        var prompt = prompts.stream().filter(p -> "test-prompts.single".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        assertThat(prompt.name()).isEqualTo("test-prompts.single");
        assertThat(prompt.description()).isEqualTo("Test Prompts - Single");
        assertThat(prompt.arguments()).hasSize(1);

        var arg = prompt.arguments().getFirst();
        assertThat(arg.name()).isEqualTo("a");
        assertThat(arg.description()).isNull();
        assertThat(arg.required()).isTrue();
    }

    @Test
    void shouldCallWithSingleParameter() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.single".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        var result = prompt.getPrompt(Map.of("a", "test"));
        assertThat(result.description()).isEqualTo("A test prompt");
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(Role.USER);
        assertThat(message.content()).isInstanceOf(TextContent.class);
    }

    @Test
    void shouldExtractMultipleParameters() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());
        var prompt = prompts.stream().filter(p -> "test-prompts.multi".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        assertThat(prompt.name()).isEqualTo("test-prompts.multi");
        assertThat(prompt.description()).isEqualTo("Test Prompts - Multi");
        assertThat(prompt.arguments()).hasSize(2);

        var argA = prompt.arguments().getFirst();
        assertThat(argA.name()).isEqualTo("a");
        assertThat(argA.description()).isNull();
        assertThat(argA.required()).isFalse();

        var argB = prompt.arguments().get(1);
        assertThat(argB.name()).isEqualTo("b");
        assertThat(argB.description()).isNull();
        assertThat(argB.required()).isTrue();
    }

    @Test
    void shouldCallWithMultipleParameters() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.multi".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        var result = prompt.getPrompt(Map.of("a", "test", "b", "value"));
        assertThat(result.description()).isEqualTo("A test prompt");
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(Role.USER);
        assertThat(message.content()).isInstanceOf(TextContent.class);
    }

    @Test
    void returningNonGetPromptResultShouldThrowException() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.bad-return".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        Map<String, String> arguments = new HashMap<>();
        assertThatThrownBy(() -> prompt.getPrompt(arguments))
                .isInstanceOf(JsonRpcInternalErrorException.class);
    }

    @Test
    void callingWithMissingRequiredArgumentShouldThrowException() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.multi".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        var arguments = Map.of("a", "test");
        assertThatThrownBy(() -> prompt.getPrompt(arguments))
                .isInstanceOf(JsonRpcInvalidParamsException.class);
    }

    @Test
    void callingWithMissingNonRequiredArgumentShouldNotThrowException() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.multi".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        var arguments = Map.of("b", "value");
        var result = prompt.getPrompt(arguments);
        assertThat(result.description()).isEqualTo("A test prompt");
        assertThat(result.messages()).hasSize(1);
        var message = result.messages().getFirst();
        assertThat(message.role()).isEqualTo(Role.USER);
        assertThat(message.content()).isInstanceOf(TextContent.class);
    }

    @Test
    void invalidReturnTypeShouldThrowException() {
        var targetObject = new BadReturnType();
        assertThatThrownBy(() -> AnnotationMcpPrompt.createPrompts(targetObject))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void invalidParametersShouldThrowException() {
        var targetObject = new BadParameterType();
        assertThatThrownBy(() -> AnnotationMcpPrompt.createPrompts(targetObject))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void promptCallThrowingExceptionShouldThrowJsonRpcInternalError() {
        var prompts = AnnotationMcpPrompt.createPrompts(new TestPrompts());

        var prompt = prompts.stream().filter(p -> "test-prompts.evil".equals(p.name())).findFirst().orElseGet(Assertions::fail);
        HashMap<String, String> arguments = new HashMap<>();
        assertThatThrownBy(() -> prompt.getPrompt(arguments))
                .isInstanceOf(JsonRpcInternalErrorException.class);
    }

}