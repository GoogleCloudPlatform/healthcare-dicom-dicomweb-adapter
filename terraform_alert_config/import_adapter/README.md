# Prepare workspace
## gcloud
Information about installation [gcloud installation](https://cloud.google.com/sdk/docs/install)
## Terraform
Information about installation [Terraform installation](https://www.terraform.io/downloads.html)
## Kubernetes
Information about installation [Kubernetes installation](https://kubernetes.io/docs/tasks/tools/install-kubectl/)
# Description
Terraform configuration for deploying alerting on DICOM Adapter health. Only the import adapter is in scope initially.

Import adapter alert on the following error events:
* CSTORE_ERROR
* CFIND_ERROR
* CFIND_QIDORS_ERROR
* CMOVE_ERROR
* CMOVE_QIDORS_ERROR
* CMOVE_CSTORE_ERROR
* COMMITMENT_ERROR
* COMMITMENT_QIDORS_ERROR

### Notification alerting
The configuration provides the ability to send notifications to the user in the event of an event, which is defined in the *terraform.tfvar* file.

### Health check
The configuration provides the ability to control the cluster uptime and send the corresponding event to the monitoring system.

# Configuration
The main connection settings and parameters are located in the *terraform.tfvars* file. Here the user can specify and change the settings for the following blocks:
* Google Cloud Project settings
* Google Cloud Storage settings
* Healthcare settigs
* Dicom import adapter setting
* Google Kubernetes Engine settings

# Validate Terraform configuration
You can check the configuration. To do this, run:
```bash
# load dependencies
terraform init -backend=false

# validate configuration
terraform validate
```

# Initialize Terraform workspace
After user have saved your customized variables file, initialize your Terraform workspace, which will download the provider and initialize it with the values provided in your `terraform.tfvars` file:
```bash
terraform init
```
In user`s initialized directory, run *terraform apply* and review the planned actions. Your terminal output should indicate the plan is running and what resources will be created:
```bash
terraform apply
```
User can see this terraform apply will provision a GKE Cluster and a GKE node pool. If you're comfortable with this, confirm the run with a `yes`.

Once the apply is complete, verify the Import Adapter service is running:
```bash
kubectl get services
```
And verify the Import Adapter deployment has replicas.
```bash
kubectl get deployments
```

Run the destroy command and confirm with `yes` in your terminal.
```bash
terraform destroy
```
