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
package com.callibrity.mocapi.resources.content;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceContentsTest {

    @Test
    void shouldCreateTextResourceContents() {
        var content = new TextResourceContents("test://uri", "Hello World", "text/plain");
        
        assertThat(content.getUri()).isEqualTo("test://uri");
        assertThat(content.getText()).isEqualTo("Hello World");
        assertThat(content.getMimeType()).isEqualTo("text/plain");
        assertThat(content.getMeta()).isNull();
    }

    @Test
    void shouldCreateTextResourceContentsWithMeta() {
        var meta = Map.<String, Object>of("key", "value");
        var content = new TextResourceContents("test://uri", "Hello World", "text/plain", meta);
        
        assertThat(content.getUri()).isEqualTo("test://uri");
        assertThat(content.getText()).isEqualTo("Hello World");
        assertThat(content.getMimeType()).isEqualTo("text/plain");
        assertThat(content.getMeta()).isEqualTo(meta);
    }

    @Test
    void shouldCreateBlobResourceContents() {
        var content = new BlobResourceContents("test://uri", "SGVsbG8gV29ybGQ=", "application/octet-stream");
        
        assertThat(content.getUri()).isEqualTo("test://uri");
        assertThat(content.getBlob()).isEqualTo("SGVsbG8gV29ybGQ=");
        assertThat(content.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(content.getMeta()).isNull();
    }

    @Test
    void shouldCreateBlobResourceContentsWithMeta() {
        var meta = Map.<String, Object>of("key", "value");
        var content = new BlobResourceContents("test://uri", "SGVsbG8gV29ybGQ=", "application/octet-stream", meta);
        
        assertThat(content.getUri()).isEqualTo("test://uri");
        assertThat(content.getBlob()).isEqualTo("SGVsbG8gV29ybGQ=");
        assertThat(content.getMimeType()).isEqualTo("application/octet-stream");
        assertThat(content.getMeta()).isEqualTo(meta);
    }

    @Test
    void shouldHandleNullValues() {
        var textContent = new TextResourceContents("test://uri", null, null);
        assertThat(textContent.getText()).isNull();
        assertThat(textContent.getMimeType()).isNull();
        
        var blobContent = new BlobResourceContents("test://uri", null, null);
        assertThat(blobContent.getBlob()).isNull();
        assertThat(blobContent.getMimeType()).isNull();
    }

    @Test
    void shouldHandleEmptyValues() {
        var textContent = new TextResourceContents("", "", "");
        assertThat(textContent.getUri()).isEmpty();
        assertThat(textContent.getText()).isEmpty();
        assertThat(textContent.getMimeType()).isEmpty();
        
        var blobContent = new BlobResourceContents("", "", "");
        assertThat(blobContent.getUri()).isEmpty();
        assertThat(blobContent.getBlob()).isEmpty();
        assertThat(blobContent.getMimeType()).isEmpty();
    }
}
