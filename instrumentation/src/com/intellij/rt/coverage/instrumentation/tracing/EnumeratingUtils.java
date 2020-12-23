/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.rt.coverage.instrumentation.tracing;

import com.intellij.rt.coverage.instrumentation.Instrumenter;
import com.intellij.rt.coverage.instrumentation.filters.enumerating.LineEnumeratorFilter;
import com.intellij.rt.coverage.instrumentation.filters.enumerating.NotNullAssertionsFilter;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;

import java.util.Collections;
import java.util.List;

public class EnumeratingUtils {
  public static MethodVisitor createEnumeratingVisitor(Instrumenter context, LineEnumerator enumerator, int access,
                                                       String name, String desc, String signature, String[] exceptions) {
    MethodVisitor root = enumerator;
    for (LineEnumeratorFilter filter : createFilters()) {
      if (filter.isApplicable(context, access, name, desc, signature, exceptions)) {
        filter.initFilter(root, enumerator);
        root = filter;
      }
    }
    return root;
  }

  private static List<LineEnumeratorFilter> createFilters() {
    LineEnumeratorFilter notNullFilter = new NotNullAssertionsFilter();
    return Collections.singletonList(notNullFilter);
  }
}
