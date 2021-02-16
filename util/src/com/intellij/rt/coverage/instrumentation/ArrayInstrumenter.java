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

import org.jetbrains.coverage.org.objectweb.asm.*;

/**
 * Instruments class with data array of the specified type.
 * Adds initialization check into every method of the class.
 */
public abstract class ArrayInstrumenter extends ClassVisitor {
  protected static final int ADDED_CODE_STACK_SIZE = 6;
  private static final String CLASS_INIT = "<clinit>";
  private static final int INTERFACE_FIELD_ACCESS = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC;
  private static final int CLASS_FIELD_ACCESS = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC;

  /**
   * Name of generated static field which holds coverage data.
   */
  private final String myArrayFieldName;
  private final String myArrayFieldType;

  /**
   * Name of generated static method which is called before any instrumented method
   * to ensure that {@link ArrayInstrumenter#myArrayFieldName} is initialized.
   * Required because instrumented method may be called before static initializer, e.g.
   * <pre>
   * <code>
   * public static void main(String[] args) {
   *  new B();
   * }
   *
   * class A {
   *   static B b = new B();
   * }
   *
   * class B extends A {
   *   B() {
   *     // called before B static initializer
   *   }
   * }
   * </code>
   * </pre>
   */
  private final String myArrayFieldInitName;
  protected final String myInternalClassName;
  private final boolean myJava8AndAbove;
  private final boolean myInterface;
  private final boolean myShouldCoverClinit;
  private boolean mySeenClinit = false;

  public ArrayInstrumenter(ClassReader cr, ClassVisitor classVisitor, String className,
                           String arrayFieldName, String arrayFieldType, String arrayFieldInitName,
                           boolean shouldCoverClinit) {
    super(Opcodes.API_VERSION, classVisitor);
    myArrayFieldName = arrayFieldName;
    myArrayFieldType = arrayFieldType;
    myArrayFieldInitName = arrayFieldInitName;
    myInternalClassName = className.replace('.', '/');
    myInterface = (cr.getAccess() & Opcodes.ACC_INTERFACE) != 0;
    myJava8AndAbove = (cr.readInt(4) & 0xFFFF) >= Opcodes.V1_8;
    myShouldCoverClinit = shouldCoverClinit;
  }

  /**
   * Generate code that crete and initialize data array field.
   * Name and type must be consistent with constructor parameters.
   */
  public abstract void initArray(MethodVisitor mv);

  /**
   * Create method visitor that initializes data array.
   * @param mv method visitor without instrumentation
   * @param newMv instrumenting method visitor
   */
  public MethodVisitor createMethodVisitor(final ClassVisitor cv,
                                           final MethodVisitor mv,
                                           MethodVisitor newMv,
                                           final String name) {
    if (mv == null) return null;
    if (myArrayFieldInitName.equals(name)) return mv;
    if (CLASS_INIT.equals(name)) {
      if (myInterface && (myJava8AndAbove || myShouldCoverClinit)) {
        newMv = new MethodVisitor(Opcodes.API_VERSION, newMv) {
          @Override
          public void visitCode() {
            cv.visitField(INTERFACE_FIELD_ACCESS, myArrayFieldName, myArrayFieldType, null, null);
            initArray(mv);
            mySeenClinit = true;
            super.visitCode();
          }
        };
      }
      if (!myShouldCoverClinit) {
        return newMv;
      }
    }

    return new MethodVisitor(Opcodes.API_VERSION, newMv) {
      @Override
      public void visitCode() {
        if (!myInterface) {
          mv.visitMethodInsn(Opcodes.INVOKESTATIC, myInternalClassName, myArrayFieldInitName, "()V", false);
        }
        super.visitCode();
      }
    };
  }

  protected MethodVisitor createMethodVisitor(final MethodVisitor mv,
                                              MethodVisitor newMv,
                                              final String name) {
    return createMethodVisitor(this, mv, newMv, name);
  }

  /**
   * Generate field with {@link ArrayInstrumenter#myArrayFieldType} array
   */
  public void generateMembers(ClassVisitor cv) {
    if (myInterface) {
      if (mySeenClinit) {
        //already added in <clinit>, e.g. if interface has constant
        //interface I {
        //  I DEFAULT = new I ();
        //}
        return;
      }

      if (!myJava8AndAbove) {
        //only java 8+ may contain non-abstract methods in interfaces
        //no need to instrument otherwise
        return;
      }
    }

    cv.visitField(myInterface ? INTERFACE_FIELD_ACCESS : CLASS_FIELD_ACCESS,
        myArrayFieldName, myArrayFieldType, null, null);

    if (!myInterface) {
      createInitFieldMethod(cv);
    } else {
      //interface has no clinit method
      //java 11 verifies that constants are initialized in clinit
      //let's generate it!
      generateExplicitClinitForInterfaces(cv);
    }
  }

  protected void generateMembers() {
    generateMembers(this);
  }

  /**
   * Creates method:
   * <pre>
   * <code>
   *   `access` static void {@link ArrayInstrumenter#myArrayFieldInitName}() {
   *     if ({@link ArrayInstrumenter#myArrayFieldName} == null) {
   *       {@link ArrayInstrumenter#myArrayFieldName} = new boolean[myMethodNames.size()];
   *       ...
   *     }
   *   }
   * </code>
   * </pre>
   */
  private void createInitFieldMethod(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, myArrayFieldInitName, "()V", null, null);
    mv.visitFieldInsn(Opcodes.GETSTATIC, myInternalClassName, myArrayFieldName, myArrayFieldType);

    final Label alreadyInitialized = new Label();
    mv.visitJumpInsn(Opcodes.IFNONNULL, alreadyInitialized);

    initArray(mv);

    mv.visitLabel(alreadyInitialized);

    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(ADDED_CODE_STACK_SIZE, 0);
    mv.visitEnd();
  }

  private void generateExplicitClinitForInterfaces(ClassVisitor cv) {
    MethodVisitor mv = cv.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC, CLASS_INIT, "()V", null, null);
    initArray(mv);
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(ADDED_CODE_STACK_SIZE, 0);
    mv.visitEnd();
  }

  protected String getFieldClassName() {
    return myInternalClassName;
  }
}
