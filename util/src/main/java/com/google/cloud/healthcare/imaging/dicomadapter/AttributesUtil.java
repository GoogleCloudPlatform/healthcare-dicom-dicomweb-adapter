package com.google.cloud.healthcare.imaging.dicomadapter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class AttributesUtil {

  private static final String PN_ALPHABETIC = "Alphabetic";
  private static final String PN_IDEOGRAPHIC = "Ideographic";
  private static final String PN_PHONETIC = "Phonetic";
  private static final String PN_DELIMITER = "=";

  public static String getTagValue(JSONObject json, String tag) throws JSONException {
    JSONObject jsonTag = json.getJSONObject(tag);
    JSONArray valueArray = jsonTag.getJSONArray("Value");
    if (valueArray.length() != 1) {
      throw new JSONException(
          "Expected single value for tag: "
              + tag
              + " in JSON:\n"
              + valueArray.toString());
    }
    return valueArray.getString(0);
  }

  public static String getTagValueOrNull(JSONObject json, String tag) {
    if (!json.has(tag)) {
      return null;
    }
    JSONObject jsonTag = json.getJSONObject(tag);
    if (!jsonTag.has("Value")) {
      return null;
    }
    JSONArray valueArray = jsonTag.getJSONArray("Value");
    if (valueArray.length() != 1) {
      return null;
    }
    return valueArray.getString(0);
  }

  /**
   * Returns array of QIDO-RS paths (more than 1 if multiple ModalitiesInStudy are present)
   *
   * @param attrs dcm4che Attributes to convert
   * @param includeFields additonal includeFields for QIDO-RS
   */
  public static String[] attributesToQidoPathArray(Attributes attrs, String... includeFields)
      throws IOException {
    if (!attrs.containsValue(Tag.ModalitiesInStudy)
        || attrs.getStrings(Tag.ModalitiesInStudy).length <= 1) {
      return new String[]{attributesToQidoPath(attrs, includeFields)};
    }

    List<String> result = new ArrayList<>();
    for (String modality : attrs.getStrings(Tag.ModalitiesInStudy)) {
      Attributes attrsCopy = new Attributes(attrs);
      attrsCopy.setString(Tag.ModalitiesInStudy, VR.CS, modality);
      result.add(attributesToQidoPath(attrsCopy, includeFields));
    }
    return result.toArray(new String[]{});
  }

  /**
   * Returns corresponding QIDO-RS path
   *
   * @param attrs dcm4che Attributes to convert
   * @param includeFields additonal includeFields for QIDO-RS
   */
  public static String attributesToQidoPath(Attributes attrs, String... includeFields)
      throws DicomServiceException {
    HashSet<Integer> nonEmptyKeys = new HashSet<>();
    HashSet<String> includeFieldSet = new HashSet<>(Arrays.asList(includeFields));
    // SpecificCharacterSet is not supported, and passing it as param or include would be wrong
    attrs.remove(Tag.SpecificCharacterSet);
    for (int tag : attrs.tags()) {
      if (attrs.containsValue(tag)) {
        nonEmptyKeys.add(tag);
      } else {
        includeFieldSet.add(TagUtils.toHexString(tag));
      }
    }

    StringBuilder qidoPath = new StringBuilder();
    if (nonEmptyKeys.contains(Tag.QueryRetrieveLevel)) {
      switch (attrs.getString(Tag.QueryRetrieveLevel)) {
        case "STUDY":
          qidoPath.append("studies");
          break;
        case "SERIES":
          qidoPath.append("series");
          break;
        case "IMAGE":
          qidoPath.append("instances");
          break;
        default:
          throw new DicomServiceException(Status.ProcessingFailure,
              "Invalid QueryRetrieveLevel specified");
      }
      nonEmptyKeys.remove(Tag.QueryRetrieveLevel);
    } else {
      throw new DicomServiceException(Status.ProcessingFailure, "No QueryRetrieveLevel specified");
    }

    if (nonEmptyKeys.size() > 0 || includeFieldSet.size() > 0) {
      qidoPath.append("?");
    }

    if (includeFieldSet.size() > 0) {
      for (String includeField : includeFieldSet) {
        qidoPath.append("includefield=" + includeField + "&");
      }
    }

    for (int keyTag : nonEmptyKeys) {
      // non-string type search keys don't seem to exist
      // pass-through multiple values (valid for UID lists, ignored for rest)
      for (String value : attrs.getStrings(keyTag)) {
        String encodedValue;
        try {
          encodedValue = URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new DicomServiceException(Status.ProcessingFailure, e);
        }
        qidoPath.append(TagUtils.toHexString(keyTag) + "=" + encodedValue + "&");
      }
    }

    return qidoPath.toString();
  }

  /**
   * Converts QIDO_RS json reply to dcm4che Attributes
   */
  public static Attributes jsonToAttributes(JSONObject jsonDataset) throws DicomServiceException {
    Attributes attributes = new Attributes();
    for (String tag : jsonDataset.keySet()) {
      JSONObject element = jsonDataset.getJSONObject(tag);
      int tagInt = TagUtils.forName(tag);
      VR vr = VR.valueOf(element.getString("vr"));
      if (isBinaryVr(vr)) {
        throw new DicomServiceException(Status.ProcessingFailure,
            "Binary VR not supported: " + vr.toString());
      } else if (element.has("Value")) {
        setAttributeValue(attributes, tagInt, vr, (JSONArray) element.get("Value"));
      } else {
        attributes.setValue(tagInt, vr, null);
      }
    }
    return attributes;
  }

  private static boolean isBinaryVr(VR vr) {
    switch (vr) {
      case OB:
      case OD:
      case OF:
      case OL:
      case OW:
      case UN:
        return true;
      default:
        return false;
    }
  }

  private static void setAttributeValue(Attributes attrs, int tag, VR vr, JSONArray jsonValues)
      throws DicomServiceException {
    if (vr == VR.PN) {
      setPatientNames(attrs, tag, jsonValues);
    } else if (vr.isStringType() || vr == VR.AT) {
      String[] values = jsonValues.toList().stream().map(Object::toString)
          .collect(Collectors.toList()).toArray(new String[]{});
      attrs.setString(tag, vr, values);
    } else if (vr.isIntType()) {
      int[] values = jsonValues.toList().stream().mapToInt(i -> Integer.parseInt(i.toString()))
          .toArray();
      attrs.setInt(tag, vr, values);
    } else {
      switch (vr) {
        case SQ:
          Sequence sequence = attrs.newSequence(tag, jsonValues.length());
          for (Object seqElement : jsonValues) {
            Attributes seqElementAttributes = jsonToAttributes((JSONObject) seqElement);
            sequence.add(seqElementAttributes);
          }
          break;
        case FL:
        case FD:
          double[] dValues = jsonValues.toList().stream().mapToDouble(i -> (Double) i).toArray();
          attrs.setDouble(tag, vr, dValues);
          break;
        default:
          throw new DicomServiceException(Status.ProcessingFailure,
              "Unsupported VR " + vr.toString());
      }
    }
  }

  private static void setPatientNames(Attributes attrs, int tag, JSONArray jsonValues) {
    List<String> results = new ArrayList<>();
    for (Object itemObj : jsonValues) {
      JSONObject item = (JSONObject) itemObj;
      String alphabetic = item.has(PN_ALPHABETIC) ? item.getString(PN_ALPHABETIC) : "";
      String ideographic = item.has(PN_IDEOGRAPHIC) ? item.getString(PN_IDEOGRAPHIC) : "";
      String phonetic = item.has(PN_PHONETIC) ? item.getString(PN_PHONETIC) : "";
      StringBuilder result = new StringBuilder();
      result.append(alphabetic);
      if (ideographic.length() > 0 || phonetic.length() > 0) {
        result.append(PN_DELIMITER);
      }
      result.append(ideographic);
      if (phonetic.length() > 0) {
        result.append(PN_DELIMITER);
      }
      result.append(phonetic);
      results.add(result.toString());
    }

    attrs.setString(tag, VR.PN, results.toArray(new String[0]));
  }
}
