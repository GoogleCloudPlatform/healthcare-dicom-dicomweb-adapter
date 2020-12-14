#!/bin/bash
export PATH=/opt/apache-maven-3.6.3/bin:$PATH

cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescu &&
mvn install -ntp &&
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-movescu &&
mvn install -ntp &&
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-findscu &&
mvn install -ntp &&
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescp &&
mvn install -ntp &&
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-stgcmtscu &&
mvn install -ntp &&
cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-dcm2xml &&
mvn install -ntp

chmod -R 777 /workspace/dcm4che
