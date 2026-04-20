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
package com.callibrity.mocapi.security.spring;

import static org.assertj.core.api.Assertions.assertThat;

import com.callibrity.mocapi.server.guards.Guard;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SpringSecurityGuardsTest {

  static class Fixture {

    /** Fixture target annotated with {@link RequiresScope} only. */
    @RequiresScope("admin:write")
    public void scopeOnly() {
      /* empty reflection target — see class javadoc. */
    }

    /** Fixture target annotated with {@link RequiresRole} only. */
    @RequiresRole("ADMIN")
    public void roleOnly() {
      /* empty reflection target — see class javadoc. */
    }

    /** Fixture target annotated with both {@link RequiresScope} and {@link RequiresRole}. */
    @RequiresScope("admin:write")
    @RequiresRole("ADMIN")
    public void both() {
      /* empty reflection target — see class javadoc. */
    }

    /** Fixture target with no security annotations. */
    public void neither() {
      /* empty reflection target — see class javadoc. */
    }
  }

  @Test
  void attaches_only_scope_guard_when_only_scope_annotation_present() throws Exception {
    List<Guard> attached = attachFor("scopeOnly");
    assertThat(attached).hasSize(1);
    assertThat(attached.get(0)).isInstanceOf(ScopeGuard.class);
  }

  @Test
  void attaches_only_role_guard_when_only_role_annotation_present() throws Exception {
    List<Guard> attached = attachFor("roleOnly");
    assertThat(attached).hasSize(1);
    assertThat(attached.get(0)).isInstanceOf(RoleGuard.class);
  }

  @Test
  void attaches_both_guards_when_both_annotations_present() throws Exception {
    List<Guard> attached = attachFor("both");
    assertThat(attached).hasSize(2);
    assertThat(attached.get(0)).isInstanceOf(ScopeGuard.class);
    assertThat(attached.get(1)).isInstanceOf(RoleGuard.class);
  }

  @Test
  void attaches_nothing_when_neither_annotation_present() throws Exception {
    assertThat(attachFor("neither")).isEmpty();
  }

  private static List<Guard> attachFor(String methodName) throws NoSuchMethodException {
    Method method = Fixture.class.getMethod(methodName);
    Object config = new Object();
    List<Guard> collected = new ArrayList<>();
    SpringSecurityGuards.attach(config, method, (cfg, guard) -> collected.add(guard));
    return collected;
  }
}
