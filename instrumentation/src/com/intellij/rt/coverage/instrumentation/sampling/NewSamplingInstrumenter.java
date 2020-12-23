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

package com.intellij.rt.coverage.instrumentation.sampling;

import com.intellij.rt.coverage.data.LineData;
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.instrumentation.ArrayInstrumenter;
import com.intellij.rt.coverage.instrumentation.InstrumentationUtils;
import com.intellij.rt.coverage.instrumentation.Instrumenter;
import com.intellij.rt.coverage.util.LinesUtil;
import org.jetbrains.coverage.org.objectweb.asm.*;

public class NewSamplingInstrumenter extends Instrumenter {
    private static final String LINE_HITS_FIELD_NAME = "__$lineHits$__";
    private static final String LINE_HITS_FIELD_TYPE = "[I";
    private static final String LINE_HITS_FIELD_INIT_NAME = "__$lineHitsInit$__";

    private final String myClassNameType;
    private final ClassReader myReader;
    private final ArrayInstrumenter myArrayInstrumenter;

    public NewSamplingInstrumenter(final ProjectData projectData,
                                   final ClassVisitor classVisitor,
                                   final ClassReader cr,
                                   final String className,
                                   final boolean shouldCalculateSource) {
        super(projectData, classVisitor, className, shouldCalculateSource);
        myArrayInstrumenter = new ArraySamplingInstrumenter(cr, className);
        myClassNameType = className.replace(".", "/");
        myReader = cr;
    }

    public MethodVisitor createMethodLineEnumerator(
        final MethodVisitor mv,
        final String name,
        final String desc,
        final int access,
        final String signature,
        final String[] exceptions
    ) {
        final MethodVisitor visitor = new MethodVisitor(Opcodes.API_VERSION, mv) {
            public void visitLineNumber(final int line, final Label start) {
                getOrCreateLineData(line, name, desc);
                myArrayInstrumenter.incrementByIndex(mv, line);
                super.visitLineNumber(line, start);
            }
        };
        return myArrayInstrumenter.createMethodVisitor(this, mv, visitor, name);
    }

    @Override
    public void visitEnd() {
        myArrayInstrumenter.generateMembers(this);
        super.visitEnd();
    }

    @Override
    protected void initLineData() {
        final LineData[] lines = LinesUtil.calcLineArray(myMaxLineNumber, myLines);
        myClassData.setLines(lines);
    }

    private class ArraySamplingInstrumenter extends ArrayInstrumenter {

        public ArraySamplingInstrumenter(ClassReader cr, String className) {
            super(cr, null, className, LINE_HITS_FIELD_NAME, LINE_HITS_FIELD_TYPE, LINE_HITS_FIELD_INIT_NAME, true);
        }

        public void initArray(MethodVisitor mv) {
            myMaxLineNumber = new LineCounter(NewSamplingInstrumenter.this).calcMaxLineNumber(myReader);
            mv.visitLdcInsn(getClassName());
            InstrumentationUtils.pushInt(mv, myMaxLineNumber + 1);
            mv.visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);

            //register line array
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "touchClassLines", "(Ljava/lang/String;[I)[I", false);

            //ensure same line array loaded in different class loaders
            mv.visitFieldInsn(Opcodes.PUTSTATIC, myClassNameType, LINE_HITS_FIELD_NAME, LINE_HITS_FIELD_TYPE);
        }
    }
}
