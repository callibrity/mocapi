/*
 * Copyright © 2025-2026 Callibrity, Inc. (contactus@callibrity.com)
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
package com.callibrity.mocapi.server.resources;

import com.callibrity.mocapi.model.ReadResourceResult;

/**
 * Produces the {@link ReadResourceResult} for a fixed-URI resource on {@code resources/read}
 * (ADR-0035). It is the unit of "how to read this resource"; a {@link ReadResourceHandler} pairs
 * one with the resource's descriptor and guards. The reflective, annotation-scanned form wraps the
 * method's {@code MethodInvoker}; other readers (e.g. contributed static file serving) are just
 * other implementations.
 */
@FunctionalInterface
public interface ResourceReader {

  ReadResourceResult read();
}
