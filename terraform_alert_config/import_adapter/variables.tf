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

variable "threshold_value" {
  description = "threshold_value"
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
