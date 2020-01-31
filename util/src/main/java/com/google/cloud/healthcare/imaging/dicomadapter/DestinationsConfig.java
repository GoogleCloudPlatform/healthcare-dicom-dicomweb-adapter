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

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DestinationsConfig {

  private static final String ENV_DESTINATION_JSON = "DESTINATION_CONFIG_JSON";
  private static Logger log = LoggerFactory.getLogger(DestinationsConfig.class);

  private HashMap<String, String> map = new LinkedHashMap<>();

  /**
   * Creates DestinationsConfig based on provided path to json or environment variable
   * if both parameters absent, will try ENV_DESTINATIONS_JSON
   * @param jsonInline checked 1st
   * @param jsonPath checked 2nd
   */
  public DestinationsConfig(String jsonInline, String jsonPath) throws IOException {
    JSONArray jsonArray = JsonUtil.parseConfig(jsonInline, jsonPath, ENV_DESTINATION_JSON);

    if(jsonArray != null) {
      for (Object elem : jsonArray) {
        JSONObject elemJson = (JSONObject) elem;
        String filter = elemJson.getString("filter");
        if(map.containsKey(filter)){
          throw new IllegalArgumentException("Duplicate filter in Destinations config");
        }

        map.put(filter, elemJson.getString("dicomweb_destination"));
      }
    }

    log.info("DestinationsConfig map = {}", map);
  }

  public Map<String, String> getMap() {
    return map;
  }
}
