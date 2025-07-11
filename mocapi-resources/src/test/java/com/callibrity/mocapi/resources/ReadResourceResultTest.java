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
package com.callibrity.mocapi.resources;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadResourceResultTest {

    @Test
    void shouldCreateTextResultWithMimeType() {
        var result = ReadResourceResult.text("Hello World", "text/plain");
        
        assertThat(result.text()).isEqualTo("Hello World");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldCreateTextResultWithDefaultMimeType() {
        var result = ReadResourceResult.text("Hello World");
        
        assertThat(result.text()).isEqualTo("Hello World");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldCreateBlobResult() {
        var result = ReadResourceResult.blob("SGVsbG8gV29ybGQ=", "application/octet-stream");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isEqualTo("SGVsbG8gV29ybGQ=");
        assertThat(result.mimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldCreateBlobResultWithImageMimeType() {
        var result = ReadResourceResult.blob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==", "image/png");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isEqualTo("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        assertThat(result.mimeType()).isEqualTo("image/png");
    }

    @Test
    void shouldHandleNullTextContent() {
        var result = ReadResourceResult.text(null, "text/plain");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldHandleNullBlobContent() {
        var result = ReadResourceResult.blob(null, "application/octet-stream");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldHandleEmptyTextContent() {
        var result = ReadResourceResult.text("", "text/plain");
        
        assertThat(result.text()).isEqualTo("");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("text/plain");
    }

    @Test
    void shouldHandleEmptyBlobContent() {
        var result = ReadResourceResult.blob("", "application/octet-stream");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isEqualTo("");
        assertThat(result.mimeType()).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldCreateTextResultWithJsonMimeType() {
        var result = ReadResourceResult.text("{\"key\": \"value\"}", "application/json");
        
        assertThat(result.text()).isEqualTo("{\"key\": \"value\"}");
        assertThat(result.blob()).isNull();
        assertThat(result.mimeType()).isEqualTo("application/json");
    }

    @Test
    void shouldCreateBlobResultWithPdfMimeType() {
        var result = ReadResourceResult.blob("JVBERi0xLjQK", "application/pdf");
        
        assertThat(result.text()).isNull();
        assertThat(result.blob()).isEqualTo("JVBERi0xLjQK");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
    }
}
