import os
import random
import string

ERROR_CODE = 88

def runCommand(command, message):
    result = os.system(command)
    print(message, result)
    return result

def get_random_string(length):
    letters = string.ascii_lowercase
    result_str = ''.join(random.choice(letters) for i in range(length))
    print("Store name is:", result_str)
    return result_str

# get current host from env
def get_host():
   return os.environ.get("HOST")

# clear data
def clear_data():
    return runCommand("rm -R dcm4che", "remove dcm4che")

# install environment
def install_environvent():
    return runCommand("./integration_test/scripts/install-env.sh", "install environvent exit with")

# clone-dcm4che
def clone_dcm4che():
    return runCommand("git clone https://github.com/dcm4che/dcm4che.git dcm4che", "clone-dcm4che exit with")

# checkout-dcm4che-tag
def checkout_dcm4che_tag():
    return runCommand("cd dcm4che && git checkout \"tags/5.15.1\"", "checkout-dcm4che-tag exit with")

# build-tools
def build_tools():
    return runCommand("./integration_test/scripts/mvn-install-tools.sh", "build-tools exit with")

# store-scp
def store_scp(store_csp_run_step, store_scp_port, close_store_scp_run_step):
    return runCommand("./integration_test/scripts/run-store-scp-and-wait.sh " + store_csp_run_step + " "+store_scp_port+" "+close_store_scp_run_step, "store-scp exit with")

# build adapter
def build_adapter():
    return runCommand("./integration_test/scripts/build-adapters.sh", "build adapter exit with")

# build adapter image
def build_adapter_image(imageproject):
    return runCommand("./integration_test/scripts/build-adapter-image.sh "+imageproject+" local_run", "build adapter image exit with")

# setup-dataset-and-dicom-store
#     Create two dicom stores for this test, each with a random name.
def setup_dataset_and_dicom_store(project, location, dataset, store_name):
    return runCommand("./integration_test/scripts/setup-dataset-and-store.sh "+project+" "+location+" "+dataset+" "+store_name, "setup-dataset-and-dicom-store exit with")

# run import adapter
def run_import_adapter(adapter_port, version, project, location, dataset, store_name, store_scp_run_step, store_scp_port, com_scu_step, com_scu_port, imageproject, sha):
    return runCommand("./integration_test/scripts/run-import-adapter.sh "+adapter_port+" "+version+" "+project+" "+location+" "+dataset+" "+store_name+" "+store_scp_run_step+" "+store_scp_port+" "+com_scu_step+" "+com_scu_port+" "+imageproject+" "+sha+" &", "run import adapter exit with")

# run import adapter with http2 mode
def run_import_adapter_http2(adapter_port, version, project, location, dataset, store_name ,imageproject, sha):
    return runCommand("./integration_test/scripts/run-import-adapter-http2.sh "+adapter_port+" "+version+" "+project+" "+location+" "+dataset+" "+store_name+" "+imageproject+" "+sha+" &", "run import adapter exit with")

# run import adapter with local backup mode
def run_import_adapter_local_backup(adapter_port, version, project, location, dataset, store_name):
    return runCommand("./integration_test/scripts/run-import-adapter-local-backup.sh "+adapter_port+" "+version+" "+project+" "+location+" "+dataset+" "+store_name+" &", "run import adapter exit with")

# run import adapter with gsc backup mode
def run_import_adapter_gcs_backup(adapter_port, version, project, location, dataset, store_name, bucket):
    return runCommand("./integration_test/scripts/run-import-adapter-gcs-backup.sh "+adapter_port+" "+version+" "+project+" "+location+" "+dataset+" "+store_name+" "+bucket+" &", "run import adapter exit with")

# wait-for-adapter
# wait-for-storescp
def wait_for_port(host, port):
    return runCommand("./integration_test/scripts/wait-for-port.sh "+host+" " + port, "wait-for-adapter exit with")

# run-store-scu
def run_store_scu(adapter_run_step, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd dcm4che/dcm4che-tool/dcm4che-tool-storescu &&"
           "mvn -ntp exec:java -Dexec.mainClass=org.dcm4che3.tool.storescu.StoreSCU -Dexec.args=-\"c IMPORTADAPTER@"+adapter_run_step+":"+adapter_port+" ../../../integration_test/data/example.dcm\"", "run-store-scu exit with")

# run-store-scu-destination2
def run_store_scu_destination2(host, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-storescu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.storescu.StoreSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" ../../../integration_test/data/example-mg.dcm\"", "run-store-scu-destination2 exit with")

# run-find-scu-instance
def run_find_scu_instance(host, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-findscu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.findscu.FindSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" -L IMAGE -X --out-cat --out-file findscu-instance-result.xml --out-dir ../../../integration_test/\"", "run-find-scu-instance exit with")

# run-find-scu-series
def run_find_scu_series(host, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-findscu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.findscu.FindSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" -L SERIES -X --out-cat --out-file findscu-series-result.xml --out-dir ../../../integration_test/\"", "run-find-scu-series exit with")

# run-find-scu-study
def run_find_scu_study(host, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-findscu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.findscu.FindSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" -L STUDY -X --out-cat --out-file findscu-study-result.xml --out-dir ../../../integration_test/\"", "run-find-scu-study exit with")

# run-move-scu
def run_move_scu(host, adapter_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-movescu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.movescu.MoveSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" --dest STORESCP\"", "run-move-scu exit with")

# run-commitment-scu
def run_commitment_scu(host, adapter_port, com_scu_port):
    return runCommand("export PATH=/opt/apache-maven-3.6.3/bin:$PATH &&"
           "cd /workspace/dcm4che/dcm4che-tool/dcm4che-tool-stgcmtscu &&"
           "mvn -ntp  exec:java -Dexec.mainClass=org.dcm4che3.tool.stgcmtscu.StgCmtSCU -Dexec.args=\"-c IMPORTADAPTER@"+host+":"+adapter_port+" -b STGCMTSCU:"+com_scu_port+" --explicit-vr --directory /workspace/integration_test/commitment_result /workspace/integration_test/data/example-redacted-jp2k.dcm\"", "run-commitment-scu exit with")

# close-store-scp
def close_store_scp(store_scp_run_step, close_store_scp_port):
    return runCommand("apt-get install -y netcat && nc -z "+store_scp_run_step+" "+close_store_scp_port, "close-store-scp exit with")

# check-store-curl
def check_store_curl(version, project, location, dataset, store_name, replaced_uid):
    return runCommand("integration_test/scripts/curl-dcm.sh https://healthcare.googleapis.com/"+version+"/projects/"+project+"/locations/"+location+"/datasets/"+dataset+"/dicomStores/"+store_name+"/dicomWeb/studies/"+replaced_uid+"/series/"+replaced_uid+"/instances/"+replaced_uid+" integration_test/downloaded.dcm", "check-store-curl exit with")

# check-store-diff
def check_store_diff(diffFileName):
    return runCommand("diff integration_test/downloaded.dcm "+diffFileName, "check-store-diff exit with")

# check-store-curl-destination-2
def check_store_curl_destination2(version, project, location, dataset, store_name, replaced_uid):
    return runCommand("integration_test/scripts/curl-dcm.sh https://healthcare.googleapis.com/"+version+"/projects/"+project+"/locations/"+location+"/datasets/"+dataset+"/dicomStores/"+store_name+"-destination-2/dicomWeb/studies/"+replaced_uid+"/series/"+replaced_uid+"/instances/"+replaced_uid+" integration_test/downloaded-destination-2.dcm", "check-store-curl-destination-2 exit with")

# check-store-diff-destination-2
def check_store_diff_destination2():
    return runCommand("diff integration_test/downloaded-destination-2.dcm integration_test/data/example-redacted-mg-jp2k.dcm", "check-store-diff-destination-2 exit with")

# check-find-diff-instance
def check_find_diff_instance():
    return runCommand("diff integration_test/findscu-instance-result1.xml integration_test/data/findscu-instance-expected.xml", "check-find-diff-instance exit with")

# check-find-diff-series
def check_find_diff_series():
    return runCommand("diff integration_test/findscu-series-result1.xml integration_test/data/findscu-series-expected.xml", "check-find-diff-series exit with")

# check-find-diff-study
def check_find_diff_study():
    return runCommand("diff integration_test/findscu-study-result1.xml integration_test/data/findscu-study-expected.xml", "check-find-diff-study exit with")

# check-move-diff
def check_move_diff(replaced_uid):
    return runCommand("diff integration_test/storescp-data/"+replaced_uid+" integration_test/data/example-redacted-moved-jp2k.dcm", "check-move-diff exit with")

# check-commitment-diff
def check_commitment_diff():
    return runCommand("chmod -R 777 dcm4che/dcm4che-tool/dcm4che-tool-dcm2xml &&"
           "cd dcm4che/dcm4che-tool/dcm4che-tool-dcm2xml &&"
           "/workspace/integration_test/scripts/diff-commitment-result.sh", "check-commitment-diff exit with")

# delete-dicom-store
def delete_dicom_store(store_name, project, dataset, location):
    return runCommand("gcloud beta healthcare dicom-stores delete "+store_name+" --project="+project+" --dataset="+dataset+" --location="+location+" --quiet", "delete-dicom-store "+store_name+" exit with")

# delete-dicom-store destination-2
def delete_dicom_store_destination2(store_name, project, dataset, location):
    return runCommand("gcloud beta healthcare dicom-stores delete "+store_name+"-destination-2 --project="+project+" --dataset="+dataset+" --location="+location+" --quiet", "delete-dicom-store "+store_name+"-destination2 exit with")

# verify script result
def verify_result(result):
    if result != 0:
        exit(ERROR_CODE)