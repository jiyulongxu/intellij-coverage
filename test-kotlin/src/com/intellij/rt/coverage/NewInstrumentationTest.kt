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

package com.intellij.rt.coverage

import com.intellij.rt.coverage.data.ClassData
import com.intellij.rt.coverage.data.JumpData
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.SwitchData
import com.intellij.rt.coverage.util.ProjectDataLoader
import com.intellij.rt.coverage.util.diff.CoverageDiff
import com.intellij.rt.coverage.util.diff.DiffElement
import org.junit.Assert
import org.junit.Test
import java.io.File

class NewInstrumentationTest {

    @Test
    fun testNewSamplingCoverageJoda() = assertEqualCoverage(Coverage.SAMPLING, Coverage.NEW_SAMPLING, "newInstrumentation.joda", "org\\.joda\\.time.*")

    @Test
    fun testNewSamplingCoverageApache() = assertEqualCoverage(Coverage.SAMPLING, Coverage.NEW_SAMPLING, "newInstrumentation.apache",
            patterns = "org\\.apache\\.commons.*"
                    + " -exclude" // excluded classes have fluky coverage
                    + " org\\.apache\\.commons\\.collections4\\.map\\.ReferenceMapTest"
                    + " org\\.apache\\.commons\\.collections4\\.map\\.AbstractReferenceMap")

    private fun assertEqualCoverage(before: Coverage, after: Coverage, testName: String, patterns: String) {
        val (fileA, fileB) = List(2) { createTempFile("test") }.onEach { it.deleteOnExit() }
        val projectA = runWithCoverage(fileA, testName, before, patterns = patterns)
        val projectB = runWithCoverage(fileB, testName, after, patterns = patterns)
        val diff = CoverageDiff.coverageDiff(projectA, projectB)
        Assert.assertEquals(emptyList<DiffElement<ClassData>>(), diff.classesDiff)
        Assert.assertEquals(emptyList<DiffElement<LineData>>(), diff.linesDiff)
        Assert.assertEquals(emptyList<DiffElement<JumpData>>(), diff.jumpsDiff)
        Assert.assertEquals(emptyList<DiffElement<SwitchData>>(), diff.switchesDiff)
    }

    private fun assertEqualCoverage(fileA: File, fileB: File) {
        val diff = CoverageDiff.coverageDiff(ProjectDataLoader.load(fileA), ProjectDataLoader.load(fileB))
        Assert.assertTrue(diff.isEmpty)
    }
}
