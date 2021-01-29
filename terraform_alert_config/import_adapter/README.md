## Terraform Alerting Config
This document describes the example DICOM Import adapter terraform configuration.
The terraform configuration deploys a DICOM Import adapter to Google Kubernetes
Engine, creates a Cloud Healthcare dataset and dicom store, and configures
monitoring to alert on the health of the adapter. 

A storage bucket configuration is also provided in storage.tf, which can be used to
create a GCS storage bucket to use for [DICOM adapter backups](https://github.com/GoogleCloudPlatform/healthcare-dicom-dicomweb-adapter/wiki/C-STORE-Backup-and-Retries). 
By default, this bucket is not used.

*Warning*: this configuration is for test purposes only, and will generate a random
password for the deployment and store it in the terraform state. For a real deployment,
consider a [different method](https://registry.terraform.io/providers/hashicorp/kubernetes/latest/docs#authentication)
for managing authentication to kubernetes.

## Prepare workspace
The following software must be installed to use this configuration:
1. [gcloud](https://cloud.google.com/sdk/docs/install)
2. [Terraform](https://www.terraform.io/downloads.html)
3. [Kubernetes](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
## Description
Terraform configuration for deploying alerting on DICOM Import Adapter health.

The import adapter alerts on the following error events:
* CSTORE_ERROR
* CFIND_ERROR
* CFIND_QIDORS_ERROR
* CMOVE_ERROR
* CMOVE_QIDORS_ERROR
* CMOVE_CSTORE_ERROR
* COMMITMENT_ERROR
* COMMITMENT_QIDORS_ERROR

### Notification alerting
The configuration provides the ability to send notifications to the user when an event occurs, which is defined in the *terraform.tfvar* file.

### Health check
The configuration provides the ability to monitor the cluster uptime and send the corresponding event to the monitoring system.

## Configuration
The main connection settings and parameters are located in the *terraform.tfvars* file. Here the user can specify and change the settings for the following blocks:
* Google Cloud Project settings
* Google Cloud Storage settings
* Healthcare settings
* Dicom Import Adapter settings
* Google Kubernetes Engine settings

## Validate Terraform configuration
After you have made changes to the sample configuration, you can validate the configuration for any errors. To do this, run:
```bash
# load dependencies
terraform init -backend=false

# validate configuration
terraform validate
```

## Initialize Terraform workspace
After you have saved your customized variables file, you can initialize your Terraform workspace. This will download the provider and initialize it with the values provided in your `terraform.tfvars` file:
```bash
terraform init
```
In your initialized directory, run *terraform apply* and review the planned actions. Your terminal output should indicate the plan is running and what resources will be created:
```bash
terraform apply
```
This comand will provision a GKE Cluster and a GKE node pool. If you're comfortable with this, confirm the run with a `yes`.

Once the command has completed, verify the Import Adapter service is running:
```bash
kubectl get services
```
And verify the Import Adapter deployment has replicas.
```bash
kubectl get deployments
```

If you want to remove the deployment, run the destroy command and confirm with `yes` in your terminal.
```bash
terraform destroy
```
