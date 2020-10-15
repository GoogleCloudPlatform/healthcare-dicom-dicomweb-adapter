# google_monitoring_alert_policy should be corrected
# Monitoring and Alerting policy
resource "google_monitoring_notification_channel" "basic" {
  display_name = "Error Notification Channel"
  type         = "email"
  labels = {
    email_address = var.alert_notification_email
  }
}

# Setup alerting policy
resource "google_monitoring_alert_policy" "alert_policy" {
  display_name = "Notification Alert Policy"
  notification_channels = [google_monitoring_notification_channel.basic.name]
  combiner     = "OR"
  conditions {
    display_name = "Import adapter error condition"
    condition_threshold {
      filter     = var.alert_filter
      duration   = var.alert_duration
      comparison = "COMPARISON_GT"
     aggregations {
        alignment_period   = var.alert_alignment_period
        per_series_aligner = "ALIGN_RATE"
      }
    }
  }
}

