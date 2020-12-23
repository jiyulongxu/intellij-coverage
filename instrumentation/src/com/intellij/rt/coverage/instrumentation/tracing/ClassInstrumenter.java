/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.Instrumenter;
import com.intellij.rt.coverage.instrumentation.tracing.data.BranchDataContainer;
import com.intellij.rt.coverage.util.LinesUtil;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;

public class ClassInstrumenter extends Instrumenter {
  public ClassInstrumenter(final ProjectData projectData, ClassVisitor classVisitor, String className, boolean shouldCalculateSource) {
    super(projectData, classVisitor, className, shouldCalculateSource);
  }

  protected MethodVisitor createMethodLineEnumerator(final MethodVisitor methodVisitor,
                                                     final String name,
                                                     final String desc,
                                                     final int access,
                                                     final String signature,
                                                     final String[] exceptions) {
    final BranchDataContainer branchData = new BranchDataContainer(this);
    final LineEnumerator enumerator = new LineEnumerator(this, branchData, access, name, desc, signature, exceptions);
    final MethodVisitor visitor = EnumeratingUtils.createEnumeratingVisitor(this, enumerator, access, name, desc, signature, exceptions);
    return new MethodVisitor(Opcodes.API_VERSION, visitor) {
      @Override
      public void visitEnd() {
        super.visitEnd();
        MethodVisitor visitor = !enumerator.hasExecutableLines()
            ? methodVisitor
            : new TouchCounter(methodVisitor, branchData, getClassName(), access, desc);
        enumerator.getMethodNode().accept(visitor);
      }
    };
  }

  protected void initLineData() {
    myClassData.setLines(LinesUtil.calcLineArray(myMaxLineNumber, myLines));
  }
}
