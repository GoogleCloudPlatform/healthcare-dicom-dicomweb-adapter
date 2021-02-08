#!/bin/bash

set -e
set -o pipefail

export PATH=/opt/apache-maven-3.6.3/bin:$PATH

mvn -q exec:java \
 -Dexec.mainClass=org.dcm4che3.tool.dcm2xml.Dcm2Xml \
 -Dexec.args=/workspace/integration_test/commitment_result/$(ls /workspace/integration_test/commitment_result/) \
 >/workspace/integration_test/tmp.xml
cd /workspace/integration_test
perl -pe 's|<DicomAttribute keyword="TransactionUID".*?<\/DicomAttribute>||' tmp.xml >commitment-clean.xml
diff commitment-clean.xml data/commitment-expected.xml
