package com.google.cloud.healthcare.imaging.dicomadapter;

import com.github.danieln.multipart.MultipartInput;
import com.google.cloud.healthcare.IDicomWebClient;
import java.io.InputStream;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class TestUtils {

  public static JSONObject dummyQidorsTag() {
    JSONObject tagContents = new JSONObject();
    JSONArray tagValues = new JSONArray();
    tagValues.put("1");
    tagContents.put("Value", tagValues);
    tagContents.put("vr", VR.UI.toString());
    return tagContents;
  }

  public static JSONObject dummyQidorsInstance() {
    JSONObject instance = new JSONObject();
    instance.put(TagUtils.toHexString(Tag.StudyInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SeriesInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SOPInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SOPClassUID), dummyQidorsTag());
    return instance;
  }

  public static abstract class DicomWebClientTestBase implements IDicomWebClient {

    @Override
    public MultipartInput wadoRs(String path) throws DicomWebException {
      return null;
    }

    @Override
    public void stowRs(String path, InputStream in) throws DicomWebException {

    }
  }
}
