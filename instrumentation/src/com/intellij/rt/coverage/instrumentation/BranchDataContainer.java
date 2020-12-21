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

package com.intellij.rt.coverage.instrumentation;

import com.intellij.rt.coverage.data.LineData;
import org.jetbrains.coverage.org.objectweb.asm.Label;

import java.util.HashMap;
import java.util.Map;

public class BranchDataContainer {
  private final Instrumenter myContext;

  private Label myLastFalseJump;
  private Label myLastTrueJump;
  private Map<Label, Jump> myJumps;
  private Map<Label, Switch> mySwitches;

  public BranchDataContainer(Instrumenter context) {
    myContext = context;
  }

  public void startNewMethod() {
    myLastFalseJump = null;
    myLastTrueJump = null;
  }

  public void addJump(int id, int line, Label trueLabel, Label falseLabel) {
    Jump trueJump = new Jump(id, line, true);
    Jump falseJump = new Jump(id, line, false);

    myLastTrueJump = trueLabel;
    myLastFalseJump = falseLabel;

    if (myJumps == null) myJumps = new HashMap<Label, Jump>();
    myJumps.put(myLastFalseJump, falseJump);
    myJumps.put(myLastTrueJump, trueJump);
  }

  public void addSwitch(int id, int line, Label dflt, Label[] labels) {
    if (mySwitches == null) mySwitches = new HashMap<Label, Switch>();
    mySwitches.put(dflt, new Switch(id, line, -1));
    for (int i = labels.length - 1; i >= 0; i--) {
      mySwitches.put(labels[i], new Switch(id, line, i));
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

  public Jump getJump(Label jump) {
    if (myJumps == null) return null;
    return myJumps.get(jump);
  }

  public Switch getSwitch(Label label) {
    if (mySwitches == null) return null;
    return mySwitches.get(label);
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
