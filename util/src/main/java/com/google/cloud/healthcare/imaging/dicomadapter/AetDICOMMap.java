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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AetDICOMMap {

  private static final String ENV_AETS_JSON = "ENV_AETS_DICOM_JSON";
  private static Logger log = LoggerFactory.getLogger(AetDICOMMap.class);
  private List<Aet> aetMap = new ArrayList<Aet>();

  /**
   * Creates AetDICOMMap based on provided path to json or environment variable
   * if both parameters absent, will try ENV_AETS_JSON
   * 
   * @param jsonInline checked 1st
   * @param jsonPath   checked 2nd
   */
  public AetDICOMMap(String jsonInline, String jsonPath) throws IOException {
    JSONArray jsonArray = JsonUtil.parseConfig(jsonInline, jsonPath, ENV_AETS_JSON);

    if (jsonArray != null) {
      for (Object elem : jsonArray) {
        JSONObject elemJson = (JSONObject) elem;
        String name = elemJson.getString("name");
        aetMap.add(new Aet(name, elemJson.getString("dicom"), elemJson.getInt("port")));
      }
    }

    log.info("aetMap = {}", aetMap);
  }

  public AetDICOMMap(Aet[] aets) {
    for (Aet elem : aets) {
      aetMap.add(elem);
    }
  }

  public List<Aet> getAets() {
    return this.aetMap;
  }

  public static class Aet {

    private String name;
    private String dicom;
    private int port;

    public Aet(String name, String dicom, int port) {
      this.name = name;
      this.dicom = dicom;
      this.port = port;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDicom() {
      return dicom;
    }

    public void setDicom(String dicom) {
      this.dicom = dicom;
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
          ", dicom='" + dicom + '\'' +
          ", port=" + port +
          "}";
    }
  }
}
