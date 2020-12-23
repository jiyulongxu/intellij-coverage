/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.rt.coverage.instrumentation.InstrumentationUtils;
import com.intellij.rt.coverage.instrumentation.tracing.data.BranchDataContainer;
import com.intellij.rt.coverage.instrumentation.tracing.data.Jump;
import com.intellij.rt.coverage.instrumentation.tracing.data.Switch;
import org.jetbrains.coverage.org.objectweb.asm.Label;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;
import org.jetbrains.coverage.org.objectweb.asm.Type;

public class TouchCounter extends MethodVisitor implements Opcodes {
  private final int myVariablesCount;

  private final BranchDataContainer myBranchData;
  private final String myClassName;

  private Label myStartLabel;
  private Label myEndLabel;

  public TouchCounter(final MethodVisitor methodVisitor,
                      final BranchDataContainer branchDataContainer,
                      final String className,
                      int access,
                      String desc) {
    super(Opcodes.API_VERSION, methodVisitor);
    myBranchData = branchDataContainer;
    myClassName = className;
    int variablesCount = ((Opcodes.ACC_STATIC & access) != 0) ? 0 : 1;
    final Type[] args = Type.getArgumentTypes(desc);
    for (Type arg : args) {
      variablesCount += arg.getSize();
    }
    myVariablesCount = variablesCount;
  }


  public void visitLineNumber(int line, Label start) {
    mv.visitVarInsn(Opcodes.ALOAD, getCurrentClassDataNumber());
    InstrumentationUtils.pushInt(mv, line);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "trace", "(Ljava/lang/Object;I)V", false);
    super.visitLineNumber(line, start);
  }

  public void visitLabel(Label label) {
    if (myStartLabel == null) {
      myStartLabel = label;
    }
    myEndLabel = label;

    super.visitLabel(label);

    visitPossibleJump(label);

    final Switch aSwitch = myBranchData.getSwitch(label);
    if (aSwitch != null) {
      mv.visitVarInsn(Opcodes.ALOAD, getCurrentClassDataNumber());
      InstrumentationUtils.pushInt(mv, aSwitch.getLine());
      InstrumentationUtils.pushInt(mv, aSwitch.getIndex());
      mv.visitIntInsn(Opcodes.SIPUSH, aSwitch.getKey());
      mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "touchSwitch", "(Ljava/lang/Object;III)V", false);
    }
  }

  private void touchBranch(final boolean trueHit, final int jumpIndex, int line) {
    mv.visitVarInsn(Opcodes.ALOAD, getCurrentClassDataNumber());
    InstrumentationUtils.pushInt(mv, line);
    InstrumentationUtils.pushInt(mv, jumpIndex);
    mv.visitInsn(trueHit ? Opcodes.ICONST_0 : Opcodes.ICONST_1);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "touchJump", "(Ljava/lang/Object;IIZ)V", false);
  }

  private void visitPossibleJump(Label label) {
    final Jump jump = myBranchData.getJump(label);
    if (jump != null) {
      touchBranch(jump.getType(), jump.getIndex(), jump.getLine());
    }
  }

  public void visitCode() {
    mv.visitLdcInsn(myClassName);
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "loadClassData", "(Ljava/lang/String;)Ljava/lang/Object;", false);
    mv.visitVarInsn(Opcodes.ASTORE, getCurrentClassDataNumber());

    super.visitCode();
  }

  public void visitVarInsn(int opcode, int var) {
    mv.visitVarInsn(opcode, adjustVariable(var));
  }

  public void visitIincInsn(int var, int increment) {
    mv.visitIincInsn(adjustVariable(var), increment);
  }

  public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
    mv.visitLocalVariable(name, desc, signature, start, end, adjustVariable(index));
  }

  private int adjustVariable(final int var) {
    return (var >= getCurrentClassDataNumber()) ? var + 1 : var;
  }

  public int getCurrentClassDataNumber() {
    return myVariablesCount;
  }

  public void visitMaxs(int maxStack, int maxLocals) {
    if (myStartLabel != null && myEndLabel != null) {
      mv.visitLocalVariable("__class__data__", "Ljava/lang/Object;", null, myStartLabel, myEndLabel, getCurrentClassDataNumber());
    }
    super.visitMaxs(maxStack, maxLocals);
  }
}
