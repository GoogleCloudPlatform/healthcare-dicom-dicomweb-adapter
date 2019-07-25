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

package com.google.cloud.healthcare.imaging.dicomadapter.cstoresender;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.AetDictionary;
import java.io.Closeable;
import java.io.IOException;

public interface ICStoreSender extends Closeable {

  /**
   * Sends instance via c-store (or test stub) to target AET, returns bytes sent
   */
  long cstore(
      AetDictionary.Aet target,
      String studyUid,
      String seriesUid,
      String sopInstanceUid,
      String sopClassUid)
      throws IDicomWebClient.DicomWebException, IOException, InterruptedException;
}
