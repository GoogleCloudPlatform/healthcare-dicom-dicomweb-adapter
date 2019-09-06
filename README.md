# DICOM Adapter

The DICOM adapter is a set of components that translate between traditional DICOM DIMSE protocols (e.g., C-STORE) and the RESTful DICOMweb protocols (e.g., STOW-RS). There are
two components, namely import and export adapter.


## Import Adapter

The Import Adapter converts incoming DIMSE requests to corresponding DICOMWeb requests and passes the converted results back to the DIMSE client. The following requests are supported:
- C-STORE to STOW-RS
- C-FIND to QIDO-RS
- C-MOVE uses QIDO-RS to determine which instances to transfer, then for each instance executes a 
WADO-RS request to fetch the instance and a C-STORE request to transfer it to the C-MOVE destination
- Storage commitment service to QIDO-RS

Note that any C-FIND query on the ModalitiesInStudy tag will result in 1 QIDO-RS query per modality.

Available AET destinations for the C-MOVE and storage commitment services are configured via an AET dictionary json file, 
which can be specified either by using the "--aet_dictionary" command line parameter or 
specifying the "ENV_AETS_JSON" environment variable.

The following configuration needs to be added to the dicom-adapter.yaml file to use CMOVE. 
Please see the [Deployment using Kubernetes](#deployment-using-kubernetes) section for more information.
```yaml
env:
- name: ENV_AETS_JSON
  valueFrom:
    configMapKeyRef:
      name: aet-dictionary
      key: AETs.json
```

Here is an example JSON dictionary:
```shell
[
	{
		"name": "DEVICE_A", 
		"host": "localhost", 
		"port": 11113
	},
	{
		"name": "DEVICE_B", 
		"host": "192.168.0.1", 
		"port": 11114
	},
	...
]
```

And command to create configmap from it:

```shell
kubectl create configmap aet-dictionary --from-file=AETs.json
```

The AET dictionary JSON can also be specified directly via the "--aet_dictionary_inline" parameter.

For the list of command line flags, see [here](import/src/main/java/com/google/cloud/healthcare/imaging/dicomadapter/Flags.java)

## Export Adapter

The Export Adapter listens to [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/)
for new instances, fetches them using WADO-RS, then sends them to the client.
This binary can be configured to output either C-STORE or STOW-RS via command
line flags.

To use [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/), you require a [Google Cloud project](https://cloud.google.com). Furthermore, [Cloud Pubsub API](https://console.cloud.google.com/apis/api/pubsub.googleapis.com/overview) must be enabled in your Google project. The binary expects that each Cloud Pub/Sub notification consists of the WADO-RS path for the DICOM instance that is to be exported (e.g. `/studies/<STUDY_UID>/series/<SERIES_UID>/instances/<INSTANCE_UID>`).

For the list of command line flags, see [here](export/src/main/java/com/google/cloud/healthcare/imaging/dicomadapter/Flags.java)

## Stackdriver Monitoring

Both the Import and Export adapter include support for Stackdriver Monitoring.
It is enabled by specifying the --monitoring_project_id parameter, which must be the same project in which the adapter is running.
For the list of events logged to Stackdriver for the Export Adapter, see [here](export/src/main/java/com/google/cloud/healthcare/imaging/dicomadapter/monitoring/Event.java). 
For the list of events logged to Stackdriver for the Import Adapter, see [here](import/src/main/java/com/google/cloud/healthcare/imaging/dicomadapter/monitoring/Event.java).

The monitored resource is configured as k8s_container, with values set from a combination of environment variables configured via Downward API (pod name, pod namespace and container name) and GCP Metadata (project id, cluster name and location). Defaults to the global resource, if k8s_container can't be configured.

The following configuration needs to be added to the dicom-adapter.yaml file to configure the 
stackdriver monitoring resource. Please see the [Deployment using Kubernetes](#deployment-using-kubernetes) section 
for more information.
```yaml
env:
- name: ENV_POD_NAME
  valueFrom:
    fieldRef:
      fieldPath: metadata.name
- name: ENV_POD_NAMESPACE
  valueFrom:
    fieldRef:
      fieldPath: metadata.namespace
- name: ENV_CONTAINER_NAME
  value: *containerName # referencing earlier anchor in same yaml
```

## Deployment using Kubernetes

The adapters can be deployed to Google Cloud Platform using [GKE] (https://cloud.google.com/kubernetes-engine/). We have published prebuilt Docker images for the both adapters to [Google Container Registry](https://cloud.google.com/container-registry/).

- Import Adapter: `gcr.io/cloud-healthcare-containers/dicom-import-adapter`
- Export Adapter: `gcr.io/cloud-healthcare-containers/dicom-export-adapter`

### Requirements

- A [Google Cloud project](https://cloud.google.com).
- Installed [gcloud](https://cloud.google.com/sdk/gcloud/) and [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/) command line tools.

### Deploying Docker Images to GKE

Create a local file called `dicom_adapter.yaml`. This file will contain the
configuration specifying the number of adapters to deploy, along with their
command line flags.

To deploy an Import Adapter, add the following to `dicom_adapter.yaml`. Modify
the flags for your use case.

```yaml
apiVersion: extensions/v1beta1
kind: Deployment
metadata:
  name: dicom-adapter
spec:
  replicas: 1
  template:
    metadata:
      labels:
        app: dicom-adapter
    spec:
      containers:
        - name: dicom-import-adapter
          image: gcr.io/cloud-healthcare-containers/dicom-import-adapter:latest
          ports:
            - containerPort: 2575
              protocol: TCP
              name: "port"
          command:
            - "/import/bin/import"
            - "--dimse_aet=IMPORTADAPTER"
            - "--dimse_port=2575"
            - "--dicomweb_address=https://healthcare.googleapis.com/v1beta1/projects/myproject/locations/us-central1/datasets/mydataset/dicomStores/mydicomstore/dicomWeb"
```

The dicomweb_addr and dicomweb_stow_path parameters have been deprecated, please use the dicomweb_address parameter instead as shown above.
The old address parameters will not work with C-FIND, C-MOVE, and storage commitment.

If needed, to additionally include an Export Adapter, you can add the to the
containers in `dicom_adapter.yaml`. Modify the flags for your use case.

```yaml
        - name: dicom-export-adapter
          image: gcr.io/cloud-healthcare-containers/dicom-export-adapter:latest
          command:
            - "/export/bin/export"
            - "--peer_dimse_aet=PEERAET"
            - "--peer_dimse_ip=localhost"
            - "--peer_dimse_port=104"
            - "--project_id=myproject"
            - "--subscription_id=mysub"
            - "--dicomweb_addr=https://healthcare.googleapis.com/v1beta1"
            - "--oauth_scopes=https://www.googleapis.com/auth/pubsub"
```

The peer_dicomweb_addr and peer_dicomweb_stow_path parameters have been deprecated, please use the peer_dicomweb_address parameter instead.

To deploy the configuration to GKE cluster, execute the following:

```shell
gcloud container clusters create dicom-adapter --zone=us-central1-a --scopes https://www.googleapis.com/auth/cloud-healthcare,https://www.googleapis.com/auth/pubsub
kubectl create -f dicom_adapter.yaml
```

If you are deploying an Import Adapter, you can expose the DIMSE port internally
(e.g. 2575 here). This can be done through a load
balancer. Create a `dicom_adapter_load_balancer.yaml`, and add the following:

```yaml
apiVersion: v1
kind: Service
metadata:
  name: dicom-adapter-load-balancer
  annotations:
    cloud.google.com/load-balancer-type: "Internal"
spec:
  ports:
  - port: 2575
    targetPort: 2575
    protocol: TCP
    name: port
  selector:
    app: dicom-adapter
  type: LoadBalancer
```

To deploy the load balancer, execute the following:

```shell
kubectl create -f dicom_adapter_load_balancer.yaml
```

The status and IP address of load balancer can be seen by executing:

```shell
kubectl get service dicom-adapter-load-balancer
```

## Building from source

As an alternative to using the prebuilt Docker images, you can build the adapters from source code. Both adapters exist as separate binaries and are built using [Gradle](https://gradle.org/). Please refer to these [instructions](https://gradle.org/install/) to build Gradle for your system.

For example, to build Import Adapter:

```shell
cd import
gradle build
```

For example, to additionally execute Import Adapter locally:

```shell
gradle run -Dexec.args="--dimse_aet=IMPORTADAPTER --dimse_port=4008 --dicomweb_address=http://localhost:80"
```

### Building and publishing Docker Images

To build and upload Import Adapter Docker images:

```shell
cd import
PROJECT=<Your Google Cloud Project>
TAG=gcr.io/${PROJECT}/dicom-import-adapter
gradle dockerBuildImage -Pdocker_tag=${TAG}
docker push ${TAG}
```

To build and upload Export Adapter Docker images:

```shell
cd export
PROJECT=<Your Google Cloud Project>
TAG=gcr.io/${PROJECT}/dicom-export-adapter
gradle dockerBuildImage -Pdocker_tag=${TAG}
docker push ${TAG}
```

Instructions on how to run the Import Adapter Docker image locally are available on the [wiki](https://github.com/GoogleCloudPlatform/healthcare-dicom-dicomweb-adapter/wiki/Running-Docker-image-locally).
