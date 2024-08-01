/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.healthcare.deid.remover;

import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Test basic DICOM tag removal. */
@RunWith(JUnit4.class)
public final class TagRemoverTest {
  @Rule
  public TemporaryFolder folder = new TemporaryFolder();
  @Test
  public void basicRedaction() throws Exception {
    File expectedFile = new File(
        TagRemoverTest.class.getClassLoader().getResource("basic-redacted.dcm").getFile());
    File inFile = new File(
        TagRemoverTest.class.getClassLoader().getResource("basic.dcm").getFile());
    FileUtils.copyFileToDirectory(inFile, folder.getRoot());
    String inPath = folder.getRoot() + "/basic.dcm";
    String outPath = folder.getRoot() + "/basic-redacted.dcm";
    String[] args = new String[]{"-i", inPath, "-o", outPath, "-t", "PatientName", "00081080"};
    TagRemover.main(args);
    Assert.assertTrue(FileUtils.contentEquals(expectedFile, new File(outPath)));
  }
}
