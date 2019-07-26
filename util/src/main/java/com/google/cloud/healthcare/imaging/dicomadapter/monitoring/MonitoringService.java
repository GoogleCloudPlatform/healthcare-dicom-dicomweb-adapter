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

package com.google.cloud.healthcare.imaging.dicomadapter.monitoring;

import com.google.api.Metric;
import com.google.api.MetricDescriptor;
import com.google.api.MonitoredResource;
import com.google.api.client.http.HttpRequestFactory;
import com.google.cloud.healthcare.imaging.dicomadapter.GcpMetadataUtil;
import com.google.cloud.monitoring.v3.MetricServiceClient;
import com.google.monitoring.v3.CreateTimeSeriesRequest;
import com.google.monitoring.v3.Point;
import com.google.monitoring.v3.ProjectName;
import com.google.monitoring.v3.TimeInterval;
import com.google.monitoring.v3.TimeSeries;
import com.google.monitoring.v3.TypedValue;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MonitoringService {

  private static final int DELAY = 60;

  private static final String META_LOCATION = "instance/zone";
  private static final String META_CLUSTER_NAME = "instance/attributes/cluster-name";

  private static Logger log = LoggerFactory.getLogger(MonitoringService.class);
  private static MonitoringService INSTANCE;
  private static boolean ENABLED = true;

  private final MetricServiceClient client;
  private final ScheduledExecutorService service;
  private final HashMap<IMonitoringEvent, Long> aggregateEvents;
  private final IMonitoringEvent[] monitoredEvents;
  private final MonitoredResource monitoredResource;

  private String projectId;

  private MonitoringService(String projectId, IMonitoringEvent[] monitoredEvents,
      HttpRequestFactory requestFactory) throws IOException {
    client = MetricServiceClient.create();

    aggregateEvents = new HashMap<>();

    this.projectId = projectId;
    this.monitoredEvents = monitoredEvents;

    // configure Resource
    MonitoredResource.Builder resourceBuilder = MonitoredResource.newBuilder();
    Map<String, String> resourceLabels = new HashMap<>();
    resourceLabels.put("project_id", this.projectId);

    Map<String, String> env = System.getenv();
    String podName = env.get("ENV_POD_NAME");
    String namespaceName = env.get("ENV_POD_NAMESPACE");
    String containerName = env.get("ENV_CONTAINER_NAME");
    String clusterName = GcpMetadataUtil.get(requestFactory, META_CLUSTER_NAME);
    String location = GcpMetadataUtil.get(requestFactory, META_LOCATION);
    if (location != null) {
      // GCPMetadata returns locations as "projects/[NUMERIC_PROJECT_ID]/zones/[ZONE]"
      // Only last part is necessary here.
      location = location.substring(location.lastIndexOf('/') + 1);
    }

    if (podName != null && namespaceName != null && containerName != null &&
        clusterName != null && location != null) {
      resourceLabels.put("pod_name", podName);
      resourceLabels.put("namespace_name", namespaceName);
      resourceLabels.put("container_name", containerName);
      resourceLabels.put("cluster_name", clusterName);
      resourceLabels.put("location", location);
      resourceBuilder.setType("k8s_container");
    } else {
      resourceBuilder.setType("global");
    }

    this.monitoredResource = resourceBuilder.putAllLabels(resourceLabels).build();
    log.info("monitoredResource = {}", monitoredResource);

    service = Executors.newSingleThreadScheduledExecutor();
    service.scheduleWithFixedDelay(MonitoringService.this::flush,
        DELAY, DELAY, TimeUnit.SECONDS);
  }

  public static void initialize(String projectId, IMonitoringEvent[] monitoredEvents,
      HttpRequestFactory requestFactory) throws IOException {
    if (INSTANCE != null) {
      throw new IllegalStateException("Already initialized");
    }
    INSTANCE = new MonitoringService(projectId, monitoredEvents, requestFactory);
  }

  public static void disable() {
    if (INSTANCE != null) {
      INSTANCE.shutdown();
      INSTANCE = null;
    }
    ENABLED = false;
  }

  public static void addEvent(IMonitoringEvent eventType, long value) {
    if (INSTANCE == null) {
      if (ENABLED) {
        log.warn("MonitoringService enabled, but not initialized. Skipping: {}={}",
            eventType, value);
      }
    } else {
      INSTANCE._addEvent(eventType, value);
    }
  }

  public static void addEvent(IMonitoringEvent eventType) {
    addEvent(eventType, 1L);
  }

  private void shutdown() {
    service.shutdown();
    client.shutdown();
  }

  private void _addEvent(IMonitoringEvent eventType, long value) {
    synchronized (aggregateEvents) {
      long prevValue = aggregateEvents.getOrDefault(eventType, 0L);
      aggregateEvents.put(eventType, prevValue + value);
    }
  }

  private void flush() {
    HashMap<IMonitoringEvent, Long> flushEvents = null;
    synchronized (aggregateEvents) {
      flushEvents = new HashMap<>(aggregateEvents);
      aggregateEvents.clear();
    }

    try {
      Timestamp flushTime = Timestamps.fromMillis(System.currentTimeMillis());

      List<TimeSeries> timeSeriesList = new ArrayList<>();
      for (IMonitoringEvent event : monitoredEvents) {
        TimeInterval interval = TimeInterval.newBuilder()
            .setEndTime(flushTime)
            .build();
        TypedValue value = TypedValue.newBuilder()
            .setInt64Value(flushEvents.getOrDefault(event, 0L))
            .build();
        Point point = Point.newBuilder()
            .setInterval(interval)
            .setValue(value)
            .build();

        List<Point> pointList = new ArrayList<>();
        pointList.add(point);

        Metric metric = Metric.newBuilder()
            .setType(event.getMetricName())
            .build();

        TimeSeries timeSeries = TimeSeries.newBuilder()
            .setMetric(metric)
            .setMetricKind(MetricDescriptor.MetricKind.GAUGE)
            .setResource(monitoredResource)
            .addAllPoints(pointList)
            .build();

        timeSeriesList.add(timeSeries);
      }

      ProjectName projectName = ProjectName.of(projectId);
      CreateTimeSeriesRequest request = CreateTimeSeriesRequest.newBuilder()
          .setName(projectName.toString())
          .addAllTimeSeries(timeSeriesList)
          .build();

      client.createTimeSeries(request);

      log.trace("Flushed {} non-zero time series", flushEvents.size());
      if (flushEvents.size() > 0) {
        log.info("Flushed: {}", flushEvents);
      }
    } catch (Throwable e) {
      log.error("Failed to flush time series", e);
    }
  }
}
