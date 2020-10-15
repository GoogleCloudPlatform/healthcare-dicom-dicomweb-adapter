# Google Kubernetes Engine prosessing
# Kubernetes deployment Dicom Impor Adapter
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
    replicas = var.replicas
    selector {
      match_labels = {
        App = "dicom-adapter"
      }
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

          port {
            container_port = var.dimse_port
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
           "--max_waiting_time_btw_uploads=${var.max_waiting_time_btw_uploads}",
           "--persistent_file_storage_location=${var.persistent_file_storage_location}",
           "--oauth_scopes=https://www.googleapis.com/auth/cloud-healthcare",
           "--verbose"
          ]

          env {
            name = "ENV_POD_NAME"
            value_from {
              field_ref {
                field_path = kubernetes_deployment.dicom-adapter.metadata[0].name
              }
            }
          }

          env {
            name = "ENV_POD_NAMESPACE"
            value_from {
              field_ref {
                field_path = kubernetes_deployment.dicom-adapter.metadata[0].namespace
              }
            }
          }

          env {
            name = "ENV_CONTAINER_NAME"
            value = kubernetes_deployment.dicom-adapter.spec[0].template[0].spec[0].container[0].name
          }
        }
      }
    }
  }

}

# expose LoadBalanser
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

