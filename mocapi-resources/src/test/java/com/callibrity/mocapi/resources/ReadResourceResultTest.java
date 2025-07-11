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

import com.callibrity.mocapi.resources.content.BlobResourceContents;
import com.callibrity.mocapi.resources.content.TextResourceContents;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReadResourceResultTest {

    @Test
    void shouldCreateTextResultWithMimeType() {
        var result = ReadResourceResult.text("Hello World", "text/plain", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("Hello World");
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateTextResultWithDefaultMimeType() {
        var result = ReadResourceResult.text("Hello World", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("Hello World");
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateBlobResult() {
        var result = ReadResourceResult.blob("SGVsbG8gV29ybGQ=", "application/octet-stream", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isEqualTo("SGVsbG8gV29ybGQ=");
        assertThat(blobContent.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateBlobResultWithImageMimeType() {
        var result = ReadResourceResult.blob("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==", "image/png", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isEqualTo("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==");
        assertThat(blobContent.getMimeType()).isEqualTo("image/png");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldHandleNullTextContent() {
        var result = ReadResourceResult.text(null, "text/plain", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isNull();
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldHandleNullBlobContent() {
        var result = ReadResourceResult.blob(null, "application/octet-stream", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isNull();
        assertThat(blobContent.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldHandleEmptyTextContent() {
        var result = ReadResourceResult.text("", "text/plain", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEmpty();
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldHandleEmptyBlobContent() {
        var result = ReadResourceResult.blob("", "application/octet-stream", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isEmpty();
        assertThat(blobContent.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateTextResultWithJsonMimeType() {
        var result = ReadResourceResult.text("{\"key\": \"value\"}", "application/json", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("{\"key\": \"value\"}");
        assertThat(textContent.getMimeType()).isEqualTo("application/json");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateBlobResultWithPdfMimeType() {
        var result = ReadResourceResult.blob("JVBERi0xLjQK", "application/pdf", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isEqualTo("JVBERi0xLjQK");
        assertThat(blobContent.getMimeType()).isEqualTo("application/pdf");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateTextResultWithUriParameter() {
        var result = ReadResourceResult.text("Hello World", "text/plain", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(TextResourceContents.class);
        var textContent = (TextResourceContents) content;
        assertThat(textContent.getText()).isEqualTo("Hello World");
        assertThat(textContent.getMimeType()).isEqualTo("text/plain");
        assertThat(textContent.getUri()).isEqualTo("test://uri");
    }

    @Test
    void shouldCreateBlobResultWithUriParameter() {
        var result = ReadResourceResult.blob("SGVsbG8gV29ybGQ=", "application/octet-stream", "test://uri");
        
        assertThat(result.contents()).hasSize(1);
        var content = result.contents().get(0);
        assertThat(content).isInstanceOf(BlobResourceContents.class);
        var blobContent = (BlobResourceContents) content;
        assertThat(blobContent.getBlob()).isEqualTo("SGVsbG8gV29ybGQ=");
        assertThat(blobContent.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(blobContent.getUri()).isEqualTo("test://uri");
    }
}
