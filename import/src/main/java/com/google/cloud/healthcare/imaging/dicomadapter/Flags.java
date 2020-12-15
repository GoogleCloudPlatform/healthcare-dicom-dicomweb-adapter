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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.util.ArrayList;
import java.util.List;

@Parameters(separators = "= ")
public class Flags {

  @Parameter(
      names = {"--dimse_aet"},
      description = "Title of DIMSE Application Entity."
  )
  String dimseAET = "";

  @Parameter(
      names = {"--dimse_cmove_aet"},
      description = "(Optional) Separate AET used for C-STORE calls within context of C-MOVE."
  )
  String dimseCmoveAET = "";

  @Parameter(
      names = {"--dimse_port"},
      description = "Port the server is listening to for incoming DIMSE requests."
  )
  Integer dimsePort = 0;

  @Deprecated
  @Parameter(
      names = {"--dicomweb_addr"},
      description = "Address for DicomWeb service. Deprecated and used only with C-STORE. "
          + "If dicomweb_address is also specified, it takes precedence."
  )
  String dicomwebAddr = "";

  @Deprecated
  @Parameter(
      names = {"--dicomweb_stow_path"},
      description =
          "Path to send StowRS requests for DicomWeb peer. This is appended to the contents of --dicomweb_addr flag. "
              + "Deprecated and used only with C-STORE. If dicomweb_address is also specified, it takes precedence."
  )
  String dicomwebStowPath = "";

  @Parameter(
      names = {"--dicomweb_address"},
      description = "Address for DicomWeb service. Must be a full path up to /dicomWeb if the Cloud Healthcare API is used."
  )
  String dicomwebAddress = "";

  @Parameter(
      names = {"--stow_http2"},
      description = "Whether to use HTTP 2.0 for StowRS (i.e. StoreInstances) requests. True by default."
  )
  Boolean useHttp2ForStow = false;

  @Parameter(
      names = {"--oauth_scopes"},
      description = "Comma seperated OAuth scopes used by adapter."
  )
  String oauthScopes = "";

  @Parameter(
      names = {"--verbose"},
      description = "Prints out debug messages."
  )
  boolean verbose = false;

  @Parameter(
      names = {"--aet_dictionary"},
      description = "Path to json containing aet definitions (array containing name/host/port per element)"
  )
  String aetDictionaryPath = "";

  @Parameter(
      names = {"--aet_dictionary_inline"},
      description = "Json array containing aet definitions (name/host/port per element). "
          + "Only one of aet_dictionary and aet_dictionary_inline needs to be specified."
  )
  String aetDictionaryInline = "";

  @Parameter(
      names = {"--monitoring_project_id"},
      description = "Stackdriver monitoring project id, must be the same as the project id in which the adapter is running"
  )
  String monitoringProjectId = "";

  @Parameter(
      names = {"--redact_remove_list"},
      description = "Tags to remove during C-STORE upload, comma separated. Only one of 'redact' flags may be present"
  )
  String tagsToRemove = "";

  @Parameter(
      names = {"--redact_keep_list"},
      description = "Tags to keep during C-STORE upload, comma separated. Only one of 'redact' flags may be present"
  )
  String tagsToKeep = "";

  @Parameter(
      names = {"--redact_filter_profile"},
      description = "Filter tags by predefined profile during C-STORE upload. Only one of 'redact' flags may be present. "
      + "Values: CHC_BASIC"
  )
  String tagsProfile = "";

  @Parameter(
      names = { "--help", "-h" },
      help = true,
      description = "Display help"
  )
  boolean help = false;

  @Parameter(
      names = {"--destination_config_path"},
      description = "Path to json array containing destination definitions (filter/dicomweb_destination per element)"
  )
  String destinationConfigPath = "";

  @Parameter(
      names = {"--destination_config_inline"},
      description = "Json array containing destination definitions (filter/dicomweb_destination per element). "
          + "Only one of destination_config_path and destination_config_inline needs to be specified."
  )
  String destinationConfigInline = "";

  @Parameter(
      names = {"--store_compress_to_transfer_syntax"},
      description = "Transfer Syntax to convert instances to during C-STORE upload. See Readme for list of supported syntaxes."
  )
  String transcodeToSyntax = "";
  
  @Parameter(
      names = {"--fuzzy_matching"},
      description = "negotiate fuzzy semantic person name attribute matching. False by default."
  )
  Boolean fuzzyMatching = false;

  @Parameter(
          names = {"--persistent_file_storage_location"},
          description = "temporary location for storing files before send"
  )
  String persistentFileStorageLocation = "";

  @Parameter(
          names = {"--gcs_backup_project_id"},
          description = "Google Cloud project ID"
  )
  String gcsBackupProjectId = "";

  @Parameter(
          names = {"--persistent_file_upload_retry_amount"},
          description = "upload retry amount"
  )
  Integer persistentFileUploadRetryAmount = 0;

  @Parameter(
          names = {"--min_upload_delay"},
          description = "minimum delay before upload backup file (ms)"
  )
  Integer minUploadDelay = 100;

  @Parameter(
          names = {"--max_waiting_time_between_uploads"},
          description = "maximum waiting time between uploads (ms)"
  )
  Integer maxWaitingTimeBetweenUploads = 5000;

  @Parameter(
      names = {"--http_error_codes_to_retry"},
      description = "http codes list to retry that less than 500."
  )
  List<Integer> httpErrorCodesToRetry = new ArrayList<>();

  @Parameter(
      names = {"--send_to_all_matching_destinations"},
      description = "If true, when processing C-STORE requests with a destination config specified, the adapter will " +
          "send to all matching destinations rather than the first matching destination."
  )
  Boolean sendToAllMatchingDestinations = false;

  public Flags() {
  }
}
