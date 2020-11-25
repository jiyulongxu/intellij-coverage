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
import com.intellij.rt.coverage.data.ProjectData;
import com.intellij.rt.coverage.util.LinesUtil;
import org.jetbrains.coverage.org.objectweb.asm.*;

public class NewSamplingInstrumenter extends Instrumenter {
    private static final String LINE_HITS_FIELD_NAME = "__$lineHits$__";
    private boolean myVisitedStaticBlock;

    private final String myClassNameType;
    private final ClassReader myReader;

    public NewSamplingInstrumenter(final ProjectData projectData,
                                   final ClassVisitor classVisitor,
                                   final ClassReader cr,
                                   final String className,
                                   final boolean shouldCalculateSource) {
        super(projectData, classVisitor, className, shouldCalculateSource);
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

                //prepare for store: load array and index
                visitFieldInsn(Opcodes.GETSTATIC, myClassNameType, LINE_HITS_FIELD_NAME, "[I");
                pushInstruction(mv, line);

                //load array
                visitFieldInsn(Opcodes.GETSTATIC, myClassNameType, LINE_HITS_FIELD_NAME, "[I");
                //index
                pushInstruction(mv, line);
                //load array[index]
                visitInsn(Opcodes.IALOAD);

                //increment
                visitInsn(Opcodes.ICONST_1);
                visitInsn(Opcodes.IADD);

                //stack: array, index, incremented value: store value in array[index]
                visitInsn(Opcodes.IASTORE);
                super.visitLineNumber(line, start);
            }
        };
        if ("<clinit>".equals(name)) {
            myVisitedStaticBlock = true;
            return new StaticBlockMethodVisitor(visitor);
        }
        return visitor;
    }

    public void visitEnd() {
        if (myProcess) {
            visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_TRANSIENT, LINE_HITS_FIELD_NAME, "[I", null, null);

            if (!myVisitedStaticBlock) {
                MethodVisitor mv = super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
                mv = new StaticBlockMethodVisitor(mv);
                mv.visitCode();
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(2, 0);
                mv.visitEnd();
            }
        }
        super.visitEnd();
    }

    @Override
    protected void initLineData() {
        final LineData[] lines = LinesUtil.calcLineArray(myMaxLineNumber, myLines);
        myClassData.setLines(lines);
    }

    private class StaticBlockMethodVisitor extends MethodVisitor {
        public StaticBlockMethodVisitor(MethodVisitor mv) {
            super(Opcodes.API_VERSION, mv);
            myMaxLineNumber = new LineCounter(NewSamplingInstrumenter.this).calcMaxLineNumber(myReader);
        }

        public void visitCode() {
            super.visitCode();

            visitLdcInsn(getClassName());
            pushInstruction(this, myMaxLineNumber + 1);
            visitIntInsn(Opcodes.NEWARRAY, Opcodes.T_INT);

            //register line array
            visitMethodInsn(Opcodes.INVOKESTATIC, ProjectData.PROJECT_DATA_OWNER, "touchClassLines", "(Ljava/lang/String;[I)[I", false);

            //ensure same line array loaded in different class loaders
            visitFieldInsn(Opcodes.PUTSTATIC, myClassNameType, LINE_HITS_FIELD_NAME, "[I");
        }

        public void visitMaxs(int maxStack, int maxLocals) {
            super.visitMaxs(Math.max(2, maxStack), maxLocals);
        }
    }

    private static void pushInstruction(MethodVisitor mv, int operand) {
        if (operand <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, operand);
        }
        else {
            mv.visitLdcInsn(operand);
        }
    }
}
