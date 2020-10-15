# project settings
variable "project_id" {
  description = "project id"
}

variable "region" {
  description = "region"
}

variable "zone" {
  description = "zone"
}

variable "alert_notification_email" {
  description = "alert_notification_email"
}

variable "bucket_id" {
  description = "bucket ID"
}

variable "upload_object" {
  description = "object for backup"
}

variable "dimse_port" {
  description = "dimse_port"
}

variable "dataset" {
  description = "dataset"
}

variable "store" {
  description = "store"
}

variable "alert_filter" {
  description = "alert_filter"
}

variable "alert_duration" {
  description = "alert_duration"
}

variable "alert_alignment_period" {
  description = "alert_alignment_period"
}

variable "persistent_file_storage_location" {
  description = "persistent_file_storage_location"
}

variable "persistent_file_upload_retry_amount" {
  default = 3  
  description = "persistent_file_upload_retry_amount"
}

variable "min_upload_delay" {
  default = 100  
  description = "min_upload_delay"
}

variable "max_waiting_time_btw_uploads" {
  default = 5000
  description = "max_waiting_time_btw_uploads"
}

variable "image" {
  description = "image"
}

# gke settings
variable "replicas" {
  description = "replicas"
}

variable "gke_username" {
  default     = ""
  description = "gke username"
}

variable "gke_password" {
  default     = ""
  description = "gke password"
}

variable "gke_num_nodes" {
  default     = 1
  description = "number of gke nodes"
}

variable "cluster_name_suffix" {
  description = "A suffix to append to the default cluster name"
  default     = ""
}

variable "network" {
  default = "default"

  description = "The VPC network to host the cluster in"
}

variable "subnetwork" {
  default = "default"
  description = "The subnetwork to host the cluster in"
}

variable "ip_range_pods" {
  default = "default"
  description = "The secondary ip range to use for pods"
}

variable "ip_range_services" {
  default = "us-central1-01-gke-01-services"  
  description = "The secondary ip range to use for services"
}

#variable "compute_engine_service_account" {
#  description = "Service account to associate to the nodes in the cluster"
#}