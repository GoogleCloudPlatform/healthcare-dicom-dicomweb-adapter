resource "kubernetes_deployment" "dicom-adapter" {
  metadata {
    name = "dicom-adapter"
    namespace = "namespace-dicom-import-adapter"
    labels = {
      App = "dicom-adapter"
    }
  }

  # customize nodes params
  spec {
    progress_deadline_seconds = 2147483647
    replicas = var.replicas
    revision_history_limit = 2147483647
    selector {
      match_labels = {
        App = "dicom-adapter"
      }
    }
    strategy {
      rolling_update {
        max_surge = 2
        max_unavailable = 1
      }
      type = "RollingUpdate"
    }
    template {
      metadata {
        labels = {
          App = "dicom-adapter"
        }
      }
      spec {
        # Customize dicom import adapter settings
        container {
          image = var.image
          name  = "dicom-import-adapter"
          image_pull_policy = "Always"
          port {
            container_port = var.dimse_port
            name = "port"
            protocol = "TCP"
          }

          resources {
            limits {
              cpu    = "0.5"
              memory = "512Mi"
            }
            requests {
              cpu    = "250m"
              memory = "50Mi"
            }
          }

          args = [
            "--dimse_aet=IMPORTADAPTER",
            "--dimse_port=${var.dimse_port}",
            "--monitoring_project_id=${var.project_id}",
            "--gcs_backup_project_id=${var.project_id}",
            "--dicomweb_address=https://healthcare.googleapis.com/v1/projects/${var.project_id}/locations/${var.region}/datasets/${var.dataset}/dicomStores/${var.store}/dicomWeb",
            "--persistent_file_upload_retry_amount=${var.persistent_file_upload_retry_amount}",
            "--min_upload_delay=${var.min_upload_delay}",
            "--persistent_file_storage_location=${var.persistent_file_storage_location}",
            "--oauth_scopes=https://www.googleapis.com/auth/cloud-platform",
            "--verbose"
          ]

          env {
            name = "ENV_POD_NAME"
            value = "dicom-adapter"
          }

          env {
            name = "ENV_POD_NAMESPACE"
            value = "namespace-dicom-import-adapter"
          }

          env {
            name = "ENV_CONTAINER_NAME"
            value = "dicom-import-adapter"
          }
        }
        dns_policy = "ClusterFirst"
        restart_policy = "Always"

        termination_grace_period_seconds = 300
      }
    }
  }
}

# expose LoadBalancer
resource "kubernetes_service" "dicom-adapter" {
  metadata {
    name = "service-dicom-adapter"
    namespace = "namespace-dicom-import-adapter"
  }
  spec {
    selector = {
      App = kubernetes_deployment.dicom-adapter.spec.0.template.0.metadata[0].labels.App
    }
    port {
      port        = var.dimse_port
      target_port = var.dimse_port
    }
    type = "LoadBalancer"
  }
}