#!/bin/bash
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescu
mvn install
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-movescu
mvn install
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-findscu
mvn install
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescp
mvn install
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-stgcmtscu
mvn install
