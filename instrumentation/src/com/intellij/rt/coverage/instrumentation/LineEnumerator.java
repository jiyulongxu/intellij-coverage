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
import com.intellij.rt.coverage.instrumentation.filters.enumerating.NotNullAssertionsFilter;
import com.sun.tools.corba.se.idl.constExpr.Not;
import org.jetbrains.coverage.org.objectweb.asm.Label;
import org.jetbrains.coverage.org.objectweb.asm.MethodVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;
import org.jetbrains.coverage.org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.Map;

public class LineEnumerator extends MethodVisitor implements Opcodes {
  private final ClassInstrumenter myClassInstrumenter;
  private final int myAccess;
  private final String myMethodName;
  private final String mySignature;
  private final MethodNode methodNode;

  private int myCurrentLine;
  private int myCurrentSwitch;

  private Label myLastFalseJump;
  private Label myLastTrueJump;

  private boolean myHasExecutableLines = false;
  private Map<Label, Jump> myJumps;
  private Map<Label, Switch> mySwitches;

  private final MethodVisitor myWriterMethodVisitor;

  private final HashMap<Label, SwitchData> mySwitchLabels = new HashMap<Label, SwitchData>();

  public LineEnumerator(ClassInstrumenter classInstrumenter, final MethodVisitor mv,
                        final int access,
                        final String name,
                        final String desc,
                        final String signature,
                        final String[] exceptions) {
    super(Opcodes.API_VERSION);

    methodNode = new SaveLabelsMethodNode(access, name, desc, signature, exceptions);

    MethodVisitor root = methodNode;
    for (LineEnumeratorFilter filter : FilterUtils.createLineEnumeratorFilters()) {
      if (filter.isApplicable(classInstrumenter, access, name, desc, signature, exceptions)) {
        filter.initFilter(root, this);
        root = filter;
      }
    }
    super.mv = root;

    myClassInstrumenter = classInstrumenter;
    myWriterMethodVisitor = mv;
    myAccess = access;
    myMethodName = name;
    mySignature = desc;
  }


  public void visitEnd() {
    super.visitEnd();
    methodNode.accept(!myHasExecutableLines ? myWriterMethodVisitor : new TouchCounter(this, myAccess, mySignature));
  }

  public void visitLineNumber(int line, Label start) {
    super.visitLineNumber(line, start);
    myCurrentLine = line;
    myCurrentSwitch = 0;
    myHasExecutableLines = true;
    myClassInstrumenter.getOrCreateLineData(myCurrentLine, myMethodName, mySignature);
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
        Jump trueJump = new Jump(currentJump, myCurrentLine, true);
        Jump falseJump = new Jump(currentJump, myCurrentLine, false);
        Label trueLabel = new Label();
        Label falseLabel = new Label();

        myLastTrueJump = trueLabel;
        myLastFalseJump = falseLabel;

        if (myJumps == null) myJumps = new HashMap<Label, Jump>();
        myJumps.put(myLastFalseJump, falseJump);
        myJumps.put(myLastTrueJump, trueJump);

        lineData.addJump(currentJump);

        jumpInstrumented = true;
        // a super call to filters may remove this jump, so myLastJump may be null after this line
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
      rememberSwitchLabels(dflt, labels);
      lineData.addSwitch(myCurrentSwitch++, keys);
    }
  }

  public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
    super.visitTableSwitchInsn(min, max, dflt, labels);
    if (!myHasExecutableLines) return;
    final LineData lineData = myClassInstrumenter.getLineData(myCurrentLine);
    if (lineData != null) {
      rememberSwitchLabels(dflt, labels);
      SwitchData switchData = lineData.addSwitch(myCurrentSwitch++, min, max);
      mySwitchLabels.put(dflt, switchData);
    }
  }

  private void rememberSwitchLabels(final Label dflt, final Label[] labels) {
    if (mySwitches == null) mySwitches = new HashMap<Label, Switch>();
    mySwitches.put(dflt, new Switch(myCurrentSwitch, myCurrentLine, -1));
    for (int i = labels.length - 1; i >= 0; i--) {
      mySwitches.put(labels[i], new Switch(myCurrentSwitch, myCurrentLine, i));
    }
  }

  public boolean hasExecutableLines() {
    return myHasExecutableLines;
  }

  public Jump getJump(Label jump) {
    if (myJumps == null) return null;
    return myJumps.get(jump);
  }

  public String getClassName() {
    return myClassInstrumenter.getClassName();
  }

  public MethodVisitor getWV() {
    return myWriterMethodVisitor;
  }


  public Switch getSwitch(Label label) {
    if (mySwitches == null) return null;
    return mySwitches.get(label);
  }

  public String getMethodName() {
    return myMethodName;
  }

  public void removeLastJump() {
    final LineData lineData = myClassInstrumenter.getLineData(myCurrentLine);
    if (lineData != null && myLastFalseJump != null) {
      lineData.removeJump(lineData.jumpsCount() - 1);
      myJumps.remove(myLastFalseJump);
      myJumps.remove(myLastTrueJump);
      myLastTrueJump = null;
      myLastFalseJump = null;
    }
  }

  public Map<Label, SwitchData> getSwitchLabels() {
    return mySwitchLabels;
  }

  public String getDescriptor() {
    return mySignature;
  }

  static class Jump {
    private final int myIndex;
    private final int myLine;
    private final boolean myType;

    public Jump(int index, int line, boolean type) {
      myIndex = index;
      myLine = line;
      myType = type;
    }

    public int getIndex() {
      return myIndex;
    }

    public int getLine() {
      return myLine;
    }

    public boolean getType() {
      return myType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Jump jump = (Jump) o;

      return myIndex == jump.myIndex
          && myLine == jump.myLine
          && myType == jump.myType;
    }

    @Override
    public int hashCode() {
      int result = myIndex;
      result = 31 * result + myLine;
      result = 31 * result + (myType ? 1 : 0);
      return result;
    }
  }


  static class Switch {
    private final int myIndex;
    private final int myLine;
    private final int myKey;

    public Switch(int index, int line, int key) {
      myIndex = index;
      myLine = line;
      myKey = key;
    }

    public int getIndex() {
      return myIndex;
    }

    public int getLine() {
      return myLine;
    }

    public int getKey() {
      return myKey;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Switch aSwitch = (Switch) o;

      return myIndex == aSwitch.myIndex
          && myLine == aSwitch.myLine
          && myKey == aSwitch.myKey;
    }

    @Override
    public int hashCode() {
      int result = myIndex;
      result = 31 * result + myLine;
      result = 31 * result + myKey;
      return result;
    }
  }
}
