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

package com.google.cloud.healthcare.deid.redactor;

import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos.DicomConfig;
import com.google.protobuf.TextFormat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Attributes.Visitor;
import org.dcm4che3.data.StandardElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.util.UIDUtils;

/** DicomRedactor implements basic DICOM redaction. */
public class DicomRedactor {

  /** RedactorSettings holds the settings for DICOM redaction. */
  private final class RedactorSettings {
    public Set<Integer> tagSet;
    public boolean isKeepList;
  }

  private final RedactorSettings settings;
  // Replace SOPInstanceUID, StudyInstanceUID, SeriesInstanceUID, and MediaStorageSOPInstanceUID.
  private static final List<Integer> replaceUIDs =
      new ArrayList<>(Arrays.asList(0x00080018, 0x0020000D, 0x0020000E, 0x00020003));
  private static final String CHC_BASIC_FILE = "chc_basic.textproto";

   /**
   *  Iterates over all tags in a DICOM file and redacts based on tagSet. If isKeepList is true, the
   *  tags in the tagSet are kept untouched and all others are removed. If isKeepList is false, the
   *  tags in the tagSet are removed and all others are kept untouched.
   */
  private class RedactVisitor implements Visitor {
    @Override
    public boolean visit(Attributes attrs, int tag, VR vr, Object value) {
      if (replaceUIDs.contains(tag)) {
        DicomRedactor.this.regenUID(attrs, tag);
        return true;
      }
      if ((settings.isKeepList && !settings.tagSet.contains(tag))
              || (!settings.isKeepList && settings.tagSet.contains(tag))) {
        attrs.setNull(tag, vr);
      }
      return true;
    }
  }

  /**
   * Constructs a DicomRedactor for the provided config.
   * @throws IllegalArgumentException if the configuration structure is invalid.
   */
  public DicomRedactor(DicomConfig config) throws Exception {
    this.settings = parseConfig(config);
  }

  /**
   * Constructs a DicomRedactor for the provided config and prefix for UID replacement.
   * @throws IllegalArgumentException if the configuration structure is invalid.
   */
  public DicomRedactor(DicomConfig config, String prefix) throws Exception {
    this(config);
    UIDUtils.setRoot(prefix);
  }

  /**
   * Parses DicomConfig proto to produce a RedactorSettings object.
   * @throws IllegalArgumentException if the configuration structure or tags are invalid.
   * @throws InternalError if there is an exception trying to load a predefined profile.
   */
  private RedactorSettings parseConfig(DicomConfig config) throws IllegalArgumentException {
    RedactorSettings ret = new RedactorSettings();
    DicomConfig.TagFilterList tags;
    switch(config.getTagFilterCase()) {
      case KEEP_LIST:
        ret.isKeepList = true;
        tags = config.getKeepList();
        break;
      case REMOVE_LIST:
        ret.isKeepList = false;
        tags = config.getRemoveList();
        break;
      case FILTER_PROFILE:
        switch(config.getFilterProfile()) {
          case TAG_FILTER_PROFILE_UNSPECIFIED:
          case CHC_BASIC:
            DicomConfig.Builder configBuilder = DicomConfig.newBuilder();
            TextFormat.Parser parser = TextFormat.Parser.newBuilder().build();
            final StringBuilder protoString = new StringBuilder();
            try (BufferedReader protoReader = new BufferedReader(new InputStreamReader(
                DicomRedactor.class.getClassLoader().getResourceAsStream(CHC_BASIC_FILE)))) {
              String line;
              while (true) {
                line = protoReader.readLine();
                if (line == null) {
                  break;
                }
                protoString.append(line).append('\n');
              }
              parser.merge(protoString.toString(), configBuilder);
              if (configBuilder.hasFilterProfile()) {
                throw new InternalError("Profile cannot point to another profile");
              }
            } catch (Exception e) {
              throw new InternalError("Failed to load selected profile.", e);
            }
            return parseConfig(configBuilder.build());
          default:
            throw new IllegalArgumentException("Config specifies an unrecognized profile.");
        }
      default:
        throw new IllegalArgumentException("Config does not specify a tag filtration method.");
    }
    ret.tagSet = new HashSet<Integer>();
    for (String tag : tags.getTagsList()) {
      int tagID = this.toTagID(tag);
      ret.tagSet.add(tagID);
    }
    return ret;
  }

  /**
   * Converts tag keywords and id strings to tag IDs.
   * @throws IllegalArgumentException if the tag cannot be converted.
   */
  private int toTagID(String tag) throws IllegalArgumentException {
    // Attempt to parse as a tag keyword.
    int tagNum = StandardElementDictionary.tagForKeyword(tag, null /* privateCreatorID */);
    if (tagNum != -1) {
      return tagNum;
    }
    // Attempt to parse as a tag id in string form.
    int ret;
    try {
      ret = Integer.parseInt(tag, 16);
    } catch (Exception e) {
      throw new IllegalArgumentException(String.format("Failed to recognize DICOM tag: %s", tag));
    }
    return ret;
  }

  /** Regenerates UID for the given tag. Does not check VR of tag to ensure it is a UID. */
  private void regenUID(Attributes attrs, int tag) {
    String newUID = UIDUtils.createNameBasedUID(attrs.getString(tag).getBytes());
    attrs.setString(tag, VR.UI, newUID);
  }

  /**
   * Redact the given DICOM input stream, and write the result to the given output stream.
   * @throws IOException if the input stream cannot be read or the output stream cannot be written.
   * @throws IllegalArgumentException if there is an error redacting the object.
   */
  public void redact(InputStream inStream, OutputStream outStream)
      throws IOException, IllegalArgumentException {
    Attributes metadata, dataset;
    try (DicomInputStream dicomInputStream = new DicomInputStream(inStream)) {
      dicomInputStream.setIncludeBulkData(IncludeBulkData.YES);
      dicomInputStream.setBulkDataDescriptor(BulkDataDescriptor.PIXELDATA);

      metadata = dicomInputStream.getFileMetaInformation();
      dataset = dicomInputStream.readDataset(-1 /* len */, -1 /* stop tag */);
    } catch (Exception e) {
      throw new IOException("Failed to read input DICOM object", e);
    }

    try {
      RedactVisitor visitor = new RedactVisitor();
      dataset.accept(visitor, false /* visitNestedDatasets */);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to redact one or more tags", e);
    }

    // Update UID in metadata.
    regenUID(metadata, Tag.MediaStorageSOPInstanceUID);

    // Overwrite transfer syntax if PixelData has been removed.
    String ts = metadata.getString(Tag.TransferSyntaxUID);
    if (dataset.contains(toTagID("PixelData"))
        && (!dataset.containsValue(toTagID("PixelData")))
        && (TransferSyntaxType.forUID(ts) != TransferSyntaxType.NATIVE)) {
      metadata.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);
    }

    try (DicomOutputStream dicomOutputStream =
        new DicomOutputStream(outStream, UID.ExplicitVRLittleEndian)) {
      dicomOutputStream.writeDataset(metadata, dataset);
    } catch (Exception e) {
      throw new IOException("Failed to write output DICOM object", e);
    }
  }
}
