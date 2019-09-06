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
package com.google.cloud.healthcare;

import java.util.Properties;
import org.apache.log4j.PropertyConfigurator;

/**
 * LogUtil contains utilities for logging.
 */
public class LogUtil {

  private LogUtil() {
  }

  // Print all logs emitted by log4j (as low as DEBUG level) to stdout. This
  // is useful for seeing dcm4che errors in verbose mode.
  public static void Log4jToStdout() {
    Properties log4jProperties = new Properties();
    log4jProperties.setProperty("log4j.rootLogger", "DEBUG, console");
    log4jProperties.setProperty("log4j.appender.console", "org.apache.log4j.ConsoleAppender");
    log4jProperties.setProperty("log4j.appender.console.layout", "org.apache.log4j.PatternLayout");
    log4jProperties.setProperty(
        "log4j.appender.console.layout.ConversionPattern", "%-5p %c %x - %m%n");
    // to prevent log spam by stackdriver monitoring
    log4jProperties.setProperty("log4j.logger.io.grpc.netty.shaded.io", "WARN");
    // very spammy otherwise
    log4jProperties.setProperty("log4j.logger.org.eclipse.jetty", "WARN");
    PropertyConfigurator.configure(log4jProperties);
  }
}
