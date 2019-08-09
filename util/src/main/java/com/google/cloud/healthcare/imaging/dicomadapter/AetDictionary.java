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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AetDictionary {

  private static final String ENV_AETS_JSON = "ENV_AETS_JSON";
  private static Logger log = LoggerFactory.getLogger(AetDictionary.class);
  private HashMap<String, Aet> aetMap = new HashMap<>();

  /**
   * Creates AetDictionary based on provided path to json or environment variable
   * if both parameters absent, will try ENV_AETS_JSON
   * @param jsonInline checked 1st
   * @param jsonPath checked 2nd
   */
  public AetDictionary( String jsonInline, String jsonPath) {
    try {
      JSONArray jsonArray;
      if(jsonInline != null && jsonInline.length() > 0) {
        jsonArray = new JSONArray(jsonInline);
      } else if (jsonPath != null && jsonPath.length() > 0) {
        jsonArray = new JSONArray(new String(
            Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8));
      } else {
        jsonArray = new JSONArray(System.getenv(ENV_AETS_JSON));
      }

      for (Object elem : jsonArray) {
        JSONObject elemJson = (JSONObject) elem;
        String name = elemJson.getString("name");
        aetMap.put(name, new Aet(name, elemJson.getString("host"), elemJson.getInt("port")));
      }

      log.info("aetMap = {}", aetMap);
    } catch (Throwable e) {
      log.error("Failed to load aet dictionary", e);
    }
  }

  public AetDictionary(Aet[] aets) {
    for (Aet elem : aets) {
      aetMap.put(elem.getName(), elem);
    }
  }

  public Aet getAet(String name) {
    return aetMap.get(name);
  }

  public static class Aet {

    private String name;
    private String host;
    private int port;

    public Aet(String name, String host, int port) {
      this.name = name;
      this.host = host;
      this.port = port;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getHost() {
      return host;
    }

    public void setHost(String host) {
      this.host = host;
    }

    public int getPort() {
      return port;
    }

    public void setPort(int port) {
      this.port = port;
    }

    @Override
    public String toString() {
      return "Aet{" +
          "name='" + name + '\'' +
          ", host='" + host + '\'' +
          ", port=" + port +
          "}";
    }
  }
}
