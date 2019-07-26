// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.cloud.healthcare.imaging.dicomadapter.util;

import static com.google.common.truth.Truth.assertThat;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DimseRSPHandler;

/**
 * Test utility to side-step problem of asserts being thrown in handlers (and hanging the test,
 * since dcm4che can't handle them)
 */
public class DimseRSPAssert extends DimseRSPHandler {

  private int resultStatus;
  private int wantStatus;

  public DimseRSPAssert(int msgId, int wantStatus) {
    super(msgId);
    this.wantStatus = wantStatus;
  }

  public DimseRSPAssert(Association association, int wantStatus) {
    this(association.nextMessageID(), wantStatus);
  }

  public void assertResult() {
    assertThat(resultStatus).isEqualTo(wantStatus);
  }

  @Override
  public void onDimseRSP(Association association, Attributes cmd, Attributes data) {
    super.onDimseRSP(association, cmd, data);
    resultStatus = cmd.getInt(Tag.Status, /* default status */ -1);
    // assert from here hangs test instead of failing it
  }
}
