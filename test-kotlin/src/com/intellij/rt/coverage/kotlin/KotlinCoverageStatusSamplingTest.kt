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

package com.intellij.rt.coverage.kotlin


import com.intellij.rt.coverage.Coverage
import org.junit.Ignore
import org.junit.Test


abstract class KotlinCoverageStatusAbstractSamplingTest : KotlinCoverageStatusTest() {
    @Test
    fun testDefaultArgsCovered() = test("defaultArgs.covered")

    @Test
    fun testDefaultArgsUncovered() = test("defaultArgs.uncovered")

    @Test
    fun testDefaultArgsSeveralArgs() = test("defaultArgs.severalArguments")

    @Test
    fun testSimpleInline() = test("inline.simple")

    @Test
    fun testInlineInline() = test("inline.inlineInline")

    @Test
    fun testLambdaInline() = test("inline.lambda")

    @Test
    fun testReflection() = test("inline.reflection", "Test")

    @Test
    fun testReified() = test("inline.reified")

    @Test
    fun testMultiplyFilesInline() = test("inline.multiplyFiles", "Test2Kt",
            fileName = "test2.kt")

    @Test
    @Ignore("Not implemented")
    fun testReturn() = test("returnTest")

    @Test
    fun testFileProperties() = test("properties.file")

    @Test
    @Ignore("Not implemented")
    fun testGetterAndSetterOfPropertyAreDistinguishable() = test("properties.getter_setter")

    @Test
    fun testPrimaryConstructorWithProperties() = test("properties.constructor", "A")

    @Test
    fun testDataClass() = test("dataClass", "A")

    @Test
    fun testDefaultInterfaceMember() = test("defaultInterfaceMember", "Foo\$DefaultImpls", "Bar")

    @Test
    fun testDefaultInterfaceMemberRemoveOnlyInterfaceMember() = test("defaultInterfaceMember.removeOnlyDefaultInterfaceMember", "Bar")

    @Test
    fun testDefaultInterfaceMemberJava() = test("defaultInterfaceMember.java",
            "Foo", "Bar",
            fileName = "Test.java")

    @Test
    fun testImplementationByDelegation() = test("implementationByDelegation", "Derived")

    @Test
    fun testImplementationByDelegationGeneric() = test("implementationByDelegationGeneric", "BDelegation")

    @Test
    fun testWhenMappingsSampling() = test("whenMapping.sampling")

    @Test
    fun testSealedClassConstructor() = test("sealedClassConstructor",
            "SealedClass", "SealedClassWithArgs", "ClassWithPrivateDefaultConstructor")

    @Test
    fun testUnloadedSingleFile() = test("unloaded.singleFile", "UnusedClass", calcUnloaded = true)

    @Test
    fun testUnloadedMultiFile() = test("unloaded.multiFile", "UnusedClass", calcUnloaded = true, fileName = "UnusedClass.kt")

    @Test
    fun testFunInterface() = test("funInterface", "TestKt", "TestKt\$test\$1")
}

class KotlinCoverageStatusSamplingTest : KotlinCoverageStatusAbstractSamplingTest() {
    override val coverage = Coverage.SAMPLING
}
