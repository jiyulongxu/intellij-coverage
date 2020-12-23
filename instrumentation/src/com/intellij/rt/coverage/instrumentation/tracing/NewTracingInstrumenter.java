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

import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.ArrayInstrumenter;
import com.intellij.rt.coverage.instrumentation.InstrumentationUtils;
import com.intellij.rt.coverage.instrumentation.Instrumenter;
import com.intellij.rt.coverage.instrumentation.tracing.data.BranchDataContainer;
import com.intellij.rt.coverage.instrumentation.tracing.data.Jump;
import com.intellij.rt.coverage.instrumentation.tracing.data.Switch;
import com.intellij.rt.coverage.util.LinesUtil;
import org.jetbrains.coverage.org.objectweb.asm.*;
import org.jetbrains.coverage.org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

public class NewTracingInstrumenter extends Instrumenter {
    private static final String BRANCH_HITS_FIELD_NAME = "__$branchHits$__";
    private static final String BRANCH_HITS_FIELD_TYPE = "[I";
    private static final String BRANCH_HITS_FIELD_INIT_NAME = "__$branchHitsInit$__";

    private final String myClassNameType;
    private final ArrayInstrumenter myArrayInstrumenter;
    private final List<MethodInstrumentation> myMethodInstrumentations = new ArrayList<MethodInstrumentation>();
    private final BranchDataContainer myBranchData = new BranchDataContainer(this);

    public NewTracingInstrumenter(final ProjectData projectData,
                                  final ClassVisitor classVisitor,
                                  final ClassReader cr,
                                  final String className,
                                  final boolean shouldCalculateSource) {
        super(projectData, classVisitor, className, shouldCalculateSource);
        myArrayInstrumenter = new ArrayTracingInstrumenter(cr, className);
        myClassNameType = className.replace(".", "/");
    }

    public MethodVisitor createMethodLineEnumerator(
        final MethodVisitor mv,
        final String name,
        final String desc,
        final int access,
        final String signature,
        final String[] exceptions
    ) {
        myBranchData.startNewMethod();
        final LineEnumerator enumerator = new LineEnumerator(this, myBranchData, access, name, desc, signature, exceptions);
        final MethodVisitor visitor = EnumeratingUtils.createEnumeratingVisitor(this, enumerator, access, name, desc, signature, exceptions);
        myMethodInstrumentations.add(new MethodInstrumentation(enumerator.getMethodNode(), mv));
        return visitor;
    }

    @Override
    public void visitEnd() {
        myArrayInstrumenter.generateMembers(this);
        for (MethodInstrumentation instrumentation : myMethodInstrumentations) {
            final MethodNode node = instrumentation.getNode();
            final MethodVisitor visitor = new MethodVisitor(Opcodes.API_VERSION, instrumentation.getVisitor()) {
                public void visitLineNumber(final int line, final Label start) {
                    LineData lineData = getLineData(line);
                    if (lineData != null) {
                        myArrayInstrumenter.incrementByIndex(mv, lineData.getId());
                    }
                    super.visitLineNumber(line, start);
                }

                public void visitLabel(Label label) {
                    super.visitLabel(label);
                    Jump jump = myBranchData.getJump(label);
                    if (jump != null) {
                        myArrayInstrumenter.incrementByIndex(mv, jump.getId());
                    }

                    Switch aSwitch = myBranchData.getSwitch(label);
                    if (aSwitch != null) {
                        myArrayInstrumenter.incrementByIndex(mv, aSwitch.getId());
                    }
                }
            };
            MethodVisitor arrayVisitor = myArrayInstrumenter.createMethodVisitor(this, instrumentation.getVisitor(), visitor, node.name);
            node.accept(arrayVisitor);
        }

        super.visitEnd();
    }

    @Override
    protected void initLineData() {
        final LineData[] lines = LinesUtil.calcLineArray(myMaxLineNumber, myLines);
        myClassData.setLines(lines);
    }

    private int calculateArraySize() {
        myBranchData.enumerateCoverageObjects();
        return myBranchData.getSize();
    }

    private class ArrayTracingInstrumenter extends ArrayInstrumenter {

        public ArrayTracingInstrumenter(ClassReader cr, String className) {
            super(cr, null, className, BRANCH_HITS_FIELD_NAME, BRANCH_HITS_FIELD_TYPE, BRANCH_HITS_FIELD_INIT_NAME, true);
        }

        public void initArray(MethodVisitor mv) {
            int size = calculateArraySize();
            mv.visitLdcInsn(getClassName());
            InstrumentationUtils.pushInt(mv, size);
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);

            //register line array
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "touchClassLines", "(Ljava/lang/String;[I)[I", false);

            //ensure same line array loaded in different class loaders
            mv.visitFieldInsn(Opcodes.PUTSTATIC, myClassNameType, BRANCH_HITS_FIELD_NAME, BRANCH_HITS_FIELD_TYPE);
        }
    }

    private static class MethodInstrumentation {
        private final MethodNode myNode;
        private final MethodVisitor myVisitor;

        MethodInstrumentation(MethodNode node, MethodVisitor visitor) {
            myNode = node;
            myVisitor = visitor;
        }

        public MethodNode getNode() {
            return myNode;
        }

        public MethodVisitor getVisitor() {
            return myVisitor;
        }
    }


}
