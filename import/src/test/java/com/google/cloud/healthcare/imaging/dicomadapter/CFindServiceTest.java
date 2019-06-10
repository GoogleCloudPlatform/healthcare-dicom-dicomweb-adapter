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

import com.github.danieln.multipart.MultipartInput;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.healthcare.IDicomWebClient;
import com.google.cloud.healthcare.LogUtil;
import com.google.cloud.healthcare.imaging.dicomadapter.util.DimseRSPAssert;
import com.google.cloud.healthcare.imaging.dicomadapter.util.PortUtil;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.AAssociateRQ;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCEchoSCP;
import org.dcm4che3.net.service.DicomServiceRegistry;
import org.dcm4che3.util.TagUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CFindServiceTest {

  // Server properties.
  final static String serverAET = "SERVER";
  final static String serverHostname = "localhost";

  final static String clientAET = "CLIENT";

  // Client properties.
  ApplicationEntity clientAE;

  private static JSONObject dummyQidorsInstance() {
    JSONObject instance = new JSONObject();
    instance.put(TagUtils.toHexString(Tag.StudyInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SeriesInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SOPInstanceUID), dummyQidorsTag());
    instance.put(TagUtils.toHexString(Tag.SOPClassUID), dummyQidorsTag());
    return instance;
  }

  private static JSONObject dummyQidorsTag() {
    JSONObject tagContents = new JSONObject();
    JSONArray tagValues = new JSONArray();
    tagValues.put("1");
    tagContents.put("Value", tagValues);
    tagContents.put("vr", VR.UI.toString());
    return tagContents;
  }

  @Before
  public void setUp() throws Exception {
    LogUtil.Log4jToStdout();

    Device device = new Device(clientAET);
    Connection conn = new Connection();
    device.addConnection(conn);
    clientAE = new ApplicationEntity(clientAET);
    device.addApplicationEntity(clientAE);
    clientAE.addConnection(conn);
    device.setExecutor(Executors.newSingleThreadExecutor());
    device.setScheduledExecutor(Executors.newSingleThreadScheduledExecutor());
  }

  @Test
  public void testCFindService_success() throws Exception {
    basicCFindServiceTest(new DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        JSONArray instances = new JSONArray();
        instances.put(dummyQidorsInstance());
        return instances;
      }
    }, Status.Success);
  }

  @Test
  public void testCFindService_cancel() throws Exception {
    basicCFindServiceTest(new DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        // that's not how it really happens
        throw new CancellationException();
      }
    }, Status.Cancel);
  }

  @Test
  public void testCFindService_processingFailure() throws Exception {
    basicCFindServiceTest(new DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        throw new NullPointerException();
      }
    }, Status.ProcessingFailure);
  }

  @Test
  public void testCFindService_outOfResources() throws Exception {
    basicCFindServiceTest(new DicomWebClientTestBase() {
      @Override
      public JSONArray qidoRs(String path) throws DicomWebException {
        // this got needlessly complicated, but I see no other way around
        HttpResponseException.Builder builder = new HttpResponseException.Builder(
            HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE,
            "test-generated exception",
            new HttpHeaders()
        );
        try {
          Constructor<?> ctor = HttpResponseException.class.getDeclaredConstructor(
              HttpResponseException.Builder.class);
          ctor.setAccessible(true);
          HttpResponseException responseException = (HttpResponseException)
              ctor.newInstance(builder);
          throw new DicomWebException(responseException);
        } catch (NoSuchMethodException | InstantiationException
            | IllegalAccessException | InvocationTargetException e) {
          throw new DicomWebException(e);
        }
      }
    }, Status.OutOfResources);
  }

  public void basicCFindServiceTest(IDicomWebClient serverDicomWebClient,
      int expectedStatus) throws Exception {
    // Create C-STORE DICOM server.
    int serverPort = createDicomServer(serverDicomWebClient);

    // Associate with peer AE.
    Association association =
        associate(serverHostname, serverPort,
            UID.StudyRootQueryRetrieveInformationModelFIND, UID.ExplicitVRLittleEndian);

    Attributes findData = new Attributes();
    findData.setString(Tag.QueryRetrieveLevel, VR.CS, "IMAGE");

    // Issue CMOVE
    DimseRSPAssert rspAssert = new DimseRSPAssert(association, expectedStatus);
    association.cfind(
        UID.StudyRootQueryRetrieveInformationModelFIND,
        UID.StudyRootQueryRetrieveInformationModelFIND,
        1,
        findData, // I mock IDicomWebClient anyway, AttributesUtil will be tested separately
        UID.ExplicitVRLittleEndian,
        rspAssert
    );
    association.waitForOutstandingRSP();

    // Close the association.
    association.release();
    association.waitForSocketClose();

    // since assert is here, only final status matters and PENDING ones are ignored
    rspAssert.assertResult();
  }

  private Association associate(
      String serverHostname, int serverPort, String sopClass, String syntax) throws Exception {
    AAssociateRQ rq = new AAssociateRQ();
    rq.addPresentationContext(new PresentationContext(1, sopClass, syntax));
    rq.setCalledAET(serverAET);
    Connection remoteConn = new Connection();
    remoteConn.setHostname(serverHostname);
    remoteConn.setPort(serverPort);
    return clientAE.connect(remoteConn, rq);
  }

  // Creates a DICOM service and returns the port it is listening on.
  private int createDicomServer(IDicomWebClient dicomWebClient) throws Exception {
    int serverPort = PortUtil.getFreePort();
    DicomServiceRegistry serviceRegistry = new DicomServiceRegistry();
    serviceRegistry.addDicomService(new BasicCEchoSCP());

    CFindService cFindService = new CFindService(dicomWebClient);
    serviceRegistry.addDicomService(cFindService);
    Device serverDevice = DeviceUtil.createServerDevice(serverAET, serverPort, serviceRegistry);
    serverDevice.bindConnections();
    return serverPort;
  }

  private abstract class DicomWebClientTestBase implements IDicomWebClient {

    @Override
    public MultipartInput wadoRs(String path) throws DicomWebException {
      return null;
    }

    @Override
    public void stowRs(String path, InputStream in) throws DicomWebException {

    }
  }
}
