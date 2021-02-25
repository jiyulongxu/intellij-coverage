/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

package com.intellij.rt.coverage.instrumentation;

import org.jetbrains.coverage.org.objectweb.asm.Label;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;
import org.jetbrains.coverage.org.objectweb.asm.Type;

public class InstrumentationUtils {
  public static void pushInt(MethodVisitor mv, int value) {
    if (value <= Short.MAX_VALUE) {
      mv.visitIntInsn(Opcodes.SIPUSH, value);
    } else {
      mv.visitLdcInsn(value);
    }
  }

  public static int getMethodVariablesCount(int access, String desc) {
    int variablesCount = ((Opcodes.ACC_STATIC & access) != 0) ? 0 : 1;
    final Type[] args = Type.getArgumentTypes(desc);
    for (Type arg : args) {
      variablesCount += arg.getSize();
    }
    return variablesCount;
  }

  /**
   * This method visitor inserts a variable into beginning of every method.
   */
  public static class LocalVariableInserter extends MethodVisitor {
    private final String myVariableName;
    private final String myVariableType;
    private final int myVarCount;
    private Label myStartLabel;
    private Label myEndLabel;

    public LocalVariableInserter(MethodVisitor methodVisitor, int access, String descriptor, String variableName, String variableType) {
      super(Opcodes.API_VERSION, methodVisitor);
      myVariableName = variableName;
      myVariableType = variableType;
      myVarCount = getMethodVariablesCount(access, descriptor);
    }

    public void visitLabel(Label label) {
      if (myStartLabel == null) {
        myStartLabel = label;
      }
      myEndLabel = label;
      super.visitLabel(label);
    }

    public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
      super.visitLocalVariable(name, desc, signature, start, end, adjustVariable(index));
    }

    public void visitIincInsn(int var, int increment) {
      super.visitIincInsn(adjustVariable(var), increment);
    }

    public void visitVarInsn(int opcode, int var) {
      super.visitVarInsn(opcode, adjustVariable(var));
    }

    public void visitMaxs(int maxStack, int maxLocals) {
      if (myStartLabel != null && myEndLabel != null) {
        mv.visitLocalVariable(myVariableName, myVariableType, null, myStartLabel, myEndLabel, getLocalVariableIndex());
      }
      super.visitMaxs(maxStack, maxLocals);
    }

    private int adjustVariable(final int var) {
      return (var >= myVarCount) ? var + 1 : var;
    }

    public int getLocalVariableIndex() {
      return myVarCount;
    }
  }
}
