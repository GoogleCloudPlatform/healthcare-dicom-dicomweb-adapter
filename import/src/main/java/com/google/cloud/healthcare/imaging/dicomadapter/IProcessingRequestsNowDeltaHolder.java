package com.google.cloud.healthcare.imaging.dicomadapter;

import java.util.concurrent.atomic.AtomicLong;

public interface IProcessingRequestsNowDeltaHolder {
  AtomicLong getProcessingRequestsNowDelta();
}
