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
import org.json.JSONArray;

public class JsonUtil {

  public static JSONArray parseConfig(String jsonInline, String jsonPath, String jsonEnvKey)
      throws IOException {
    JSONArray result = null;
    if (jsonInline != null && jsonInline.length() > 0) {
      result = new JSONArray(jsonInline);
    } else if (jsonPath != null && jsonPath.length() > 0) {
      result = new JSONArray(new String(
          Files.readAllBytes(Paths.get(jsonPath)), StandardCharsets.UTF_8));
    } else {
      String jsonEnvValue = System.getenv(jsonEnvKey);
      if (jsonEnvValue != null) {
        result = new JSONArray(jsonEnvValue);
      }
    }

    return result;
  }

}
