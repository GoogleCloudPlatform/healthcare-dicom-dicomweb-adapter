package com.google.cloud.healthcare.imaging.dicomadapter.redactor;

import com.google.cloud.healthcare.deid.redactor.DicomRedactor;
import com.google.cloud.healthcare.deid.redactor.protos.DicomConfigProtos.DicomConfig;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.codec.TransferSyntaxType;
import org.dcm4che3.io.BulkDataDescriptor;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomInputStream.IncludeBulkData;
import org.dcm4che3.io.DicomOutputStream;

public class StreamDicomRedactor extends DicomRedactor {

  private static final int BATCH_SIZE = 8192;

  public StreamDicomRedactor(
      DicomConfig config)
      throws Exception {
    super(config);
  }

  public StreamDicomRedactor(
      DicomConfig config, String prefix) throws Exception {
    super(config, prefix);
  }

  public boolean willRemoveTagValue(int tag) {
    return (settings.isKeepList && !settings.tagSet.contains(tag))
        || (!settings.isKeepList && settings.tagSet.contains(tag));
  }

  /**
   * Redact the given DICOM input stream, and write the result to the given output stream.
   *
   * @throws IOException if the input stream cannot be read or the output stream cannot be written.
   * @throws IllegalArgumentException if there is an error redacting the object.
   */
  public void redact(InputStream inStream, OutputStream outStream)
      throws IOException, IllegalArgumentException {
    Attributes metadata, dataset;
    RedactVisitor visitor = new RedactVisitor();
    DicomInputStream dicomInputStream = null;
    DicomOutputStream dicomOutputStream = null;
    try {
      try {
        dicomInputStream = new DicomInputStream(inStream);
        dicomInputStream.setIncludeBulkData(IncludeBulkData.YES);
        dicomInputStream.setBulkDataDescriptor(BulkDataDescriptor.PIXELDATA);

        dicomOutputStream =
            new DicomOutputStream(outStream, UID.ExplicitVRLittleEndian);

        metadata = dicomInputStream.getFileMetaInformation();
        // Update UID in metadata.
        regenUID(metadata, Tag.MediaStorageSOPInstanceUID);

        String ts = metadata.getString(Tag.TransferSyntaxUID);
        if (willRemoveTagValue(toTagID("PixelData"))
            && (TransferSyntaxType.forUID(ts) != TransferSyntaxType.NATIVE)) {
          metadata.setString(Tag.TransferSyntaxUID, VR.UI, UID.ExplicitVRLittleEndian);
        }
      } catch (Exception e) {
        throw new IOException("Failed to initialize redactor", e);
      }

      boolean firstDataset = true;
      while (true) {
        try {
          dataset = dicomInputStream.readDataset(BATCH_SIZE /* len */, -1 /* stop tag */);
        } catch (EOFException e) {
          break;
        }

        try {
          dataset.accept(visitor, false /* visitNestedDatasets */);
        } catch (Exception e) {
          throw new IllegalArgumentException("Failed to redact one or more tags", e);
        }

        if (firstDataset) {
          firstDataset = false;
          dicomOutputStream.writeDataset(metadata, dataset);
        } else {
          dicomOutputStream.writeDataset(null, dataset);
        }
      }
    } finally {
      if (dicomInputStream != null) {
        dicomInputStream.close();
      }
      if (dicomOutputStream != null) {
        dicomOutputStream.close();
      }
    }
  }
}
