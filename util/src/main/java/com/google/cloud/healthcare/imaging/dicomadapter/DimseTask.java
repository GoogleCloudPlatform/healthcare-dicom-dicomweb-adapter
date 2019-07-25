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

package com.google.cloud.healthcare.imaging.dicomadapter;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.CancelRQHandler;
import org.dcm4che3.net.pdu.PresentationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class DimseTask implements Runnable, CancelRQHandler {

  private static Logger log = LoggerFactory.getLogger(DimseTask.class);

  protected final Association as;
  protected final PresentationContext pc;
  protected final Attributes cmd;

  protected volatile boolean canceled;
  protected volatile Thread runThread;

  DimseTask(Association as, PresentationContext pc,
      Attributes cmd) {
    this.as = as;
    this.pc = pc;
    this.cmd = cmd;

    int msgId = cmd.getInt(Tag.MessageID, -1);
    as.addCancelRQHandler(msgId, this);
  }

  @Override
  public void onCancelRQ(Association as) {
    log.info(this.getClass().getSimpleName() + " onCancelRQ");

    if (!canceled) {
      canceled = true;
      synchronized (this) {
        if (runThread != null) {
          // Note that interrupt does not kill the thread and instead leads to InterruptedException
          // being thrown by most long duration methods (if used in subclasses).
          // Subclasses need to make sure to set runThread, provide response even if interrupted
          // (catch clause) and cleanup cancelRQHandler (finally clause)
          runThread.interrupt();
        }
      }
    }
  }
}
