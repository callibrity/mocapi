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
package com.callibrity.mocapi.model;

/**
 * Well-known values of the spec's open {@code ResultType} string union ({@code "complete" |
 * "input_required" | string}). Every result a server sends MUST carry a {@code resultType}; modeled
 * as a {@code String} component plus these constants because the union is open.
 */
public final class ResultTypes {

  public static final String COMPLETE = "complete";
  public static final String INPUT_REQUIRED = "input_required";

  private ResultTypes() {}
}
