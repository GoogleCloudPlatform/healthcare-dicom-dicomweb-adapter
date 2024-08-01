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

import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos.DicomConfig;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * TagRemover is a command line utility that removes tags from DICOM files.
 */
final class TagRemover {

  public static void main(String[] args) throws Exception {
    Options options = new Options();
    Option input = new Option("i", "input", true, "input DICOM file path");
    input.setRequired(true);
    options.addOption(input);
    Option output = new Option("o", "output", true, "output DICOM file path");
    output.setRequired(true);
    options.addOption(output);
    Option tags = new Option("t", "tags", true, "DICOM tags to redact");
    tags.setRequired(true);
    tags.setArgs(Option.UNLIMITED_VALUES);
    options.addOption(tags);

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp("tagremove", options);
      return;
    }

    InputStream is =
        new BufferedInputStream(new FileInputStream(new File(cmd.getOptionValue("input"))));
    OutputStream os =
        new BufferedOutputStream(new FileOutputStream(new File(cmd.getOptionValue("output"))));

    List<String> tagList = Arrays.asList(cmd.getOptionValues("tags"));
    DicomConfig config = DicomConfig.newBuilder().setRemoveList(
        DicomConfig.TagFilterList.newBuilder().addAllTags(tagList)).build();

    DicomRedactor redactor = new DicomRedactor(config);
    redactor.redact(is, os);
  }

}
