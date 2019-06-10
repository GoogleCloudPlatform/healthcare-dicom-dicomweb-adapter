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
          runThread.interrupt();
        }
      }
    }
  }
}
