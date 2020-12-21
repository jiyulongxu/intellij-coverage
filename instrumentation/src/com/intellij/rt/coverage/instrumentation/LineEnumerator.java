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

package com.intellij.rt.coverage.instrumentation;

import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.rt.coverage.instrumentation.filters.FilterUtils;
import com.intellij.rt.coverage.instrumentation.filters.enumerating.LineEnumeratorFilter;
import org.jetbrains.coverage.org.objectweb.asm.Label;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;
import org.jetbrains.coverage.org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

public class LineEnumerator extends MethodVisitor implements Opcodes {
  private final ClassInstrumenter myClassInstrumenter;
  private final String myMethodName;
  private final String myDescriptor;
  private final MethodNode myMethodNode;

  private int myCurrentLine;
  private int myCurrentSwitch;
  private boolean myHasExecutableLines = false;

  private final BranchDataContainer myBranchData;
  private final HashMap<Label, SwitchData> mySwitchLabels = new HashMap<Label, SwitchData>();

  public LineEnumerator(ClassInstrumenter classInstrumenter,
                        BranchDataContainer branchDataContainer,
                        final int access,
                        final String name,
                        final String desc,
                        final String signature,
                        final String[] exceptions) {
    super(Opcodes.API_VERSION);

    myMethodNode = new SaveLabelsMethodNode(access, name, desc, signature, exceptions);

    MethodVisitor root = myMethodNode;
    for (LineEnumeratorFilter filter : FilterUtils.createLineEnumeratorFilters()) {
      if (filter.isApplicable(classInstrumenter, access, name, desc, signature, exceptions)) {
        filter.initFilter(root, this);
        root = filter;
      }
    }
    super.mv = root;

    myClassInstrumenter = classInstrumenter;
    myBranchData = branchDataContainer;
    myMethodName = name;
    myDescriptor = desc;
  }

  public void visitLineNumber(int line, Label start) {
    super.visitLineNumber(line, start);
    myCurrentLine = line;
    myCurrentSwitch = 0;
    myHasExecutableLines = true;
    myClassInstrumenter.getOrCreateLineData(myCurrentLine, myMethodName, myDescriptor);
  }

  public void visitJumpInsn(final int opcode, final Label label) {
    if (!myHasExecutableLines) {
      super.visitJumpInsn(opcode, label);
      return;
    }
    boolean jumpInstrumented = false;
    if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR && !myMethodName.equals("<clinit>")) {
      final LineData lineData = myClassInstrumenter.getLineData(myCurrentLine);
      if (lineData != null) {
        int currentJump = lineData.jumpsCount();
        Label trueLabel = new Label();
        Label falseLabel = new Label();
        myBranchData.addJump(currentJump, myCurrentLine, trueLabel, falseLabel);

        lineData.addJump(currentJump);

        jumpInstrumented = true;
        super.visitJumpInsn(opcode, trueLabel);
        super.visitJumpInsn(Opcodes.GOTO, falseLabel);
        super.visitLabel(trueLabel);  // true hit will be inserted here
        super.visitJumpInsn(Opcodes.GOTO, label);
        super.visitLabel(falseLabel); // false hit will be inserted here
      }
    }

    if (!jumpInstrumented) {
      super.visitJumpInsn(opcode, label);
    }
  }

  public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
    super.visitLookupSwitchInsn(dflt, keys, labels);
    if (!myHasExecutableLines) return;
    final LineData lineData = myClassInstrumenter.getLineData(myCurrentLine);
    if (lineData != null) {
      myBranchData.addSwitch(myCurrentSwitch, myCurrentLine, dflt, labels);
      lineData.addSwitch(myCurrentSwitch++, keys);
    }
  }

  public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
    super.visitTableSwitchInsn(min, max, dflt, labels);
    if (!myHasExecutableLines) return;
    final LineData lineData = myClassInstrumenter.getLineData(myCurrentLine);
    if (lineData != null) {
      myBranchData.addSwitch(myCurrentSwitch, myCurrentLine, dflt, labels);
      SwitchData switchData = lineData.addSwitch(myCurrentSwitch++, min, max);
      mySwitchLabels.put(dflt, switchData);
    }
  }

  public boolean hasExecutableLines() {
    return myHasExecutableLines;
  }

  public String getClassName() {
    return myClassInstrumenter.getClassName();
  }

  public String getMethodName() {
    return myMethodName;
  }

  public MethodNode getMethodNode() {
    return myMethodNode;
  }

  public BranchDataContainer getBranchData() {
    return myBranchData;
  }

  public Map<Label, SwitchData> getSwitchLabels() {
    return mySwitchLabels;
  }

  public String getDescriptor() {
    return myDescriptor;
  }
}
