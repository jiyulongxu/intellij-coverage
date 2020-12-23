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

package com.intellij.rt.coverage.instrumentation.tracing.data;

import com.intellij.rt.coverage.data.JumpData;
import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.SwitchData;
import com.intellij.rt.coverage.instrumentation.Instrumenter;
import org.jetbrains.coverage.org.objectweb.asm.Label;

import java.util.HashMap;
import java.util.Map;

public class BranchDataContainer {
  private final Instrumenter myContext;

  private Label myLastFalseJump;
  private Label myLastTrueJump;
  private Map<Label, Jump> myJumps;
  private Map<Label, Switch> mySwitches;

  private int mySize;

  public BranchDataContainer(Instrumenter context) {
    myContext = context;
  }

  public int getSize() {
    return mySize;
  }

  public void startNewMethod() {
    myLastFalseJump = null;
    myLastTrueJump = null;
  }

  public void addJump(int id, int line, Label trueLabel, Label falseLabel, JumpData data) {
    Jump trueJump = new Jump(id, line, true, data);
    Jump falseJump = new Jump(id, line, false, data);

    myLastTrueJump = trueLabel;
    myLastFalseJump = falseLabel;

    if (myJumps == null) myJumps = new HashMap<Label, Jump>();
    myJumps.put(myLastFalseJump, falseJump);
    myJumps.put(myLastTrueJump, trueJump);
  }

  public void addSwitch(int index, int line, Label dflt, Label[] labels, SwitchData data) {
    if (mySwitches == null) mySwitches = new HashMap<Label, Switch>();
    mySwitches.put(dflt, new Switch(index, line, -1, data));
    for (int i = labels.length - 1; i >= 0; i--) {
      mySwitches.put(labels[i], new Switch(index, line, i, data));
    }
  }

  public void removeLastJump() {
    if (myLastTrueJump == null) return;
    Jump trueJump = myJumps.get(myLastTrueJump);
    int line = trueJump.getLine();
    LineData lineData = myContext.getLineData(line);
    if (lineData == null) return;

    lineData.removeJump(lineData.jumpsCount() - 1);
    myJumps.remove(myLastFalseJump);
    myJumps.remove(myLastTrueJump);
    myLastTrueJump = null;
    myLastFalseJump = null;
  }

  public void removeSwitch(Label label) {
    if (mySwitches != null) {
      mySwitches.remove(label);
    }
  }

  public Jump getJump(Label jump) {
    if (myJumps == null) return null;
    return myJumps.get(jump);
  }

  public Switch getSwitch(Label label) {
    if (mySwitches == null) return null;
    return mySwitches.get(label);
  }

  public void enumerateCoverageObjects() {
    int id = 0;
    int maxLine = myContext.getMaxLineNumber();
    for (int line = 0; line <= maxLine; line++) {
      LineData data = myContext.getLineData(line);
      if (data == null) continue;
      data.setId(id++);
    }
    if (myJumps != null) {
      for (Jump jump : myJumps.values()) {
        jump.setId(id++);
      }
    }
    if (mySwitches != null) {
      for (Switch aSwitch : mySwitches.values()) {
        aSwitch.setId(id++);
      }
    }
    mySize = id;
  }

}
