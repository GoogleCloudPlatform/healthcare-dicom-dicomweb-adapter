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

package com.google.cloud.healthcare.imaging.dicomadapter.cmove;

import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.imaging.dicomadapter.DeviceUtil;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CMoveSenderFactory implements ISenderFactory {

  private static Logger log = LoggerFactory.getLogger(CMoveSenderFactory.class);

  private final String cstoreSubAet;
  private final IDicomWebClient dicomWebClient;


  public CMoveSenderFactory(String cstoreSubAet, IDicomWebClient dicomWebClient) {
    this.cstoreSubAet = cstoreSubAet;
    this.dicomWebClient = dicomWebClient;
  }

  @Override
  public ISender create() {
    ApplicationEntity subApplicationEntity = new ApplicationEntity(cstoreSubAet);
    Connection conn = new Connection();
    DeviceUtil.createClientDevice(subApplicationEntity, conn);
    subApplicationEntity.addConnection(conn);

    return new CMoveSender(subApplicationEntity, dicomWebClient);
  }
}
