# These variables must be changed by the user to work with a personal project
# Google Cloud Project settings
project_id = "your_project_id"
region     = "your_region"
zone       = "your_zone"

# Google Cloud Storage settings
bucket_id     = "your_bucket_id"
upload_object = "backup"

# Healthcare settigs
dataset = "your_healthcare_dataset_name"
store = "your_healthcare_store_name"

# Dicom import adapter setting
# import adapter port
dimse_port = 4070  

# set the backup storage location
# you can specify a local storage path like "/tmp"
# or an address in GCS, for example gs://bucket_id/upload_folder
persistent_file_storage_location = "/tmp"
persistent_file_upload_retry_amount = 3
min_upload_delay = 100
max_waiting_time_btw_uploads = 5000

# image with Dicom Import Adapter
image      = "gcr.io/cloud-healthcare-containers/healthcare-api-dicom-dicomweb-adapter-import:0.2.17"

# Google Kubernetes Engine settings
replicas   = 1

# Monitoring and Alerting settings
# user can customize filter and set custom events
# you can add yours filters in this variable
alert_filter     = "resource.type=starts_with(\"custom.googleapis.com/dicomadapter/import\")"
alert_duration   = "60s"
alert_alignment_period  = "60s"
alert_notification_email      = "your_valid_email"
threshold_value = 0