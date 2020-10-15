# Google Cloud Storage processing
# create bucket for data backup
module "gcs_buckets" {
  source  = "terraform-google-modules/cloud-storage/google"
  version = "~> 1.7"
  project_id  = var.project_id
  names = [var.bucket_id]
  prefix = ""
  set_admin_roles = false
  #admins = [var.alert_notification_email]
  versioning = {
    first = false
  }
  #bucket_admins = {
  #  second = var.alert_notification_email
  #}
}