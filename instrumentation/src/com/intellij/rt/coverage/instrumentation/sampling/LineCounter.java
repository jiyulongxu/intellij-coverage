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

package com.intellij.rt.coverage.instrumentation.sampling;

import com.intellij.rt.coverage.instrumentation.MethodFilteringVisitor;
import org.jetbrains.coverage.org.objectweb.asm.*;

public class LineCounter {
  private final MethodFilteringVisitor myContext;
  private int myMaxLine;

  public LineCounter(MethodFilteringVisitor context) {
    myContext = context;
  }

  /** Should be called only after first <code>visitMethod</code> in <code>InstrumentingVisitor</code> has been called. */
  public int calcMaxLineNumber(ClassReader cr) {
    myMaxLine = 0;
    cr.accept(new LineVisitor(), 0);
    return myMaxLine;
  }

  private class LineVisitor extends ClassVisitor {

    public LineVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
      if (!myContext.shouldInstrumentMethod(access, name, descriptor, signature, exceptions)) return null;
      return new MethodVisitor(Opcodes.API_VERSION) {
        public void visitLineNumber(int line, Label start) {
          if (myMaxLine < line) {
            myMaxLine = line;
          }
          super.visitLineNumber(line, start);
        }
      };
    }
  }
}
