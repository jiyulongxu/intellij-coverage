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

import org.jetbrains.coverage.org.objectweb.asm.ClassReader;
import org.jetbrains.coverage.org.objectweb.asm.ClassVisitor;
import org.jetbrains.coverage.org.objectweb.asm.Opcodes;

public class ClassInfo {
  private int myAccess = 0;

  public ClassInfo(ClassReader cr) {
    cr.accept(new ClassInfoVisitor(), ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
  }

  public boolean isInterface() {
    return (myAccess & Opcodes.ACC_INTERFACE) != 0;
  }

  private class ClassInfoVisitor extends ClassVisitor {
    public ClassInfoVisitor() {
      super(Opcodes.API_VERSION);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      myAccess = access;
    }
  }
}
