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

import static com.google.common.truth.Truth.assertThat;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class AttributesUtilTest {

  @Test(expected = DicomServiceException.class)
  public void testAttributesToQidoPath_noQueryRetriveLevel() throws Exception {
    Attributes attrs = new Attributes();

    AttributesUtil.attributesToQidoPath(attrs);
  }

  @Test(expected = DicomServiceException.class)
  public void testAttributesToQidoPath_wrongQueryRetriveLevel() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "Wrong");

    AttributesUtil.attributesToQidoPath(attrs);
  }

  @Test
  public void testAttributesToQidoPath_simple() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
    attrs.setString(Tag.SOPInstanceUID, VR.UI, "123");

    String result = AttributesUtil.attributesToQidoPath(attrs);

    assertThat(result).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.SOPInstanceUID) + "=123&");
  }

  @Test
  public void testAttributesToQidoPath_urlEncode() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
    attrs.setString(Tag.PatientName, VR.CS, "%&#^ ");

    String result = AttributesUtil.attributesToQidoPath(attrs);

    assertThat(result).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.PatientName) + "=%25%26%23%5E+&");
  }

  @Test
  public void testAttributesToQidoPathArray_noModalitiesSet() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");

    String[] results = AttributesUtil.attributesToQidoPathArray(attrs);

    assertThat(results.length).isEqualTo(1);
    assertThat(results[0]).isEqualTo("instances");
  }

  @Test
  public void testAttributesToQidoPathArray_oneModalitySet() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
    attrs.setString(Tag.ModalitiesInStudy, VR.CS, "MG");

    String[] results = AttributesUtil.attributesToQidoPathArray(attrs);

    assertThat(results.length).isEqualTo(1);
    assertThat(results[0]).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.ModalitiesInStudy) + "=MG&");
  }

  @Test
  public void testAttributesToQidoPathArray_manyModalitiesSet() throws Exception {
    Attributes attrs = new Attributes();
    attrs.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
    attrs.setString(Tag.ModalitiesInStudy, VR.CS, "MG", "CS", "CR");

    String[] results = AttributesUtil.attributesToQidoPathArray(attrs);

    assertThat(results.length).isEqualTo(3);
    assertThat(results[0]).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.ModalitiesInStudy) + "=MG&");
    assertThat(results[1]).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.ModalitiesInStudy) + "=CS&");
    assertThat(results[2]).isEqualTo(
        "instances?" + TagUtils.toHexString(Tag.ModalitiesInStudy) + "=CR&");
  }

  @Test
  public void testJsonToAttributes_empty() throws Exception {
    JSONObject jsonObj = new JSONObject();

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    assertThat(attrs).isEqualTo(new Attributes());
  }

  @Test
  public void testJsonToAttributes_decimalString() throws Exception {
    String value = "12345678901234567890.1234567890";
    JSONObject jsonObj = new JSONObject("{\"" + TagUtils.toHexString(Tag.DecimalVisualAcuity)
        + "\": {\"vr\": \"DS\",\"Value\": [" + value + "]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setString(Tag.DecimalVisualAcuity, VR.DS, value);
    assertThat(attrs).isEqualTo(expected);
  }

  @Test
  public void testJsonToAttributes_integerString() throws Exception {
    // can be parsed as either string or int.
    JSONObject jsonObj = new JSONObject("{\"" + TagUtils.toHexString(Tag.InstanceNumber)
        + "\": {\"vr\": \"IS\",\"Value\": [" + Integer.MAX_VALUE + "]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expectedAsInt = new Attributes();
    expectedAsInt.setInt(Tag.InstanceNumber, VR.IS, Integer.MAX_VALUE);
    assertThat(attrs).isEqualTo(expectedAsInt);

    Attributes expectedAsString = new Attributes();
    expectedAsString.setString(Tag.InstanceNumber, VR.IS, String.valueOf(Integer.MAX_VALUE));
    assertThat(attrs).isEqualTo(expectedAsString);
  }

  @Test
  public void testJsonToAttributes_intType() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.ConcatenationFrameOffsetNumber)
        + "\": {\"vr\": \"UL\",\"Value\": [1, 1]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setInt(Tag.ConcatenationFrameOffsetNumber, VR.UL, 1, 1);
    assertThat(attrs).isEqualTo(expected);
  }

  @Test(expected = NumberFormatException.class)
  public void testJsonToAttributes_intType_notInt() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.ConcatenationFrameOffsetNumber)
        + "\": {\"vr\": \"UL\",\"Value\": [\"Gotcha\"]}}");

    AttributesUtil.jsonToAttributes(jsonObj);
  }

  @Test
  public void testJsonToAttributes_stringType() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.QueryRetrieveLevel)
        + "\": {\"vr\": \"CS\",\"Value\": [\"IMAGE\"]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");
    assertThat(attrs).isEqualTo(expected);
  }

  @Test
  public void testJsonToAttributes_patientName() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.PatientName)
        + "\": {\"vr\": \"PN\",\"Value\": [{"
        + "\"Alphabetic\": \"Yamada^Tarou\", "
        + "\"Ideographic\": \"山田^太郎\", "
        + "\"Phonetic\": \"やまだ^たろう\", "
        + "}]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setString(Tag.PatientName, VR.PN, "Yamada^Tarou=山田^太郎=やまだ^たろう");
    assertThat(attrs).isEqualTo(expected);
  }

  @Test
  public void testJsonToAttributes_double() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.EventTimeOffset)
        + "\": {\"vr\": \"FD\",\"Value\": [1.25]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setDouble(Tag.EventTimeOffset, VR.FD, 1.25);
    assertThat(attrs).isEqualTo(expected);
  }

  @Test
  public void testJsonToAttributes_float() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.DisplayedZValue)
        + "\": {\"vr\": \"FL\",\"Value\": [1.25]}}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    expected.setFloat(Tag.DisplayedZValue, VR.FL, 1.25f);
    assertThat(attrs).isEqualTo(expected);
  }

  @Test
  public void testJsonToAttributes_sequence() throws Exception {
    JSONObject jsonObj = new JSONObject("{\""
        + TagUtils.toHexString(Tag.FrameContentSequence)
        + "\": {\"vr\": \"SQ\", \"Value\": " +
        " [{ \"" + TagUtils.toHexString(Tag.DimensionIndexValues)
        + "\": {\"vr\": \"UL\", \"Value\": [1,1]} }] }}");

    Attributes attrs = AttributesUtil.jsonToAttributes(jsonObj);

    Attributes expected = new Attributes();
    Sequence sequence = expected.newSequence(Tag.FrameContentSequence, 1);
    Attributes sequenceElement = new Attributes();
    sequenceElement.setInt(Tag.DimensionIndexValues, VR.UL, 1, 1);
    sequence.add(sequenceElement);
    assertThat(attrs).isEqualTo(expected);
  }
}
