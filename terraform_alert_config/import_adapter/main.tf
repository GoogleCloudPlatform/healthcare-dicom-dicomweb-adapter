# Add required providers
provider "google" {
  project = var.project_id
  region  = var.region
  zone    = var.zone
}

# Customize kubernetes provider

data "google_compute_zones" "available" {
}

resource "random_id" "cluster_name" {
  byte_length = 10
}

resource "random_id" "username" {
  byte_length = 14
}

resource "random_id" "password" {
  byte_length = 16
}

variable "kubernetes_version" {
  default = ""
}

data "google_container_engine_versions" "supported" {
  location           = data.google_compute_zones.available.names[0]
  version_prefix     = var.kubernetes_version
}

# If the result is empty '[]', the GKE default_cluster_version will be used.
output "available_master_versions_matching_user_input" {
  value = data.google_container_engine_versions.supported.valid_master_versions
}

resource "google_container_cluster" "primary" {
  name               = "tf-acc-test-${random_id.cluster_name.hex}"
  location           = data.google_compute_zones.available.names[0]
  network            = "default"
  initial_node_count = var.replicas
  min_master_version = data.google_container_engine_versions.supported.latest_master_version
  # node version must match master version
  # https://www.terraform.io/docs/providers/google/r/container_cluster.html#node_version
  node_version       = data.google_container_engine_versions.supported.latest_master_version

  node_locations = [
    data.google_compute_zones.available.names[1],
  ]

  master_auth {
    username = random_id.username.hex
    password = random_id.password.hex
  }

  node_config {
    machine_type = "n1-standard-4"
  
    oauth_scopes = [
      "https://www.googleapis.com/auth/compute",
      "https://www.googleapis.com/auth/devstorage.read_only",
      "https://www.googleapis.com/auth/logging.write",
      "https://www.googleapis.com/auth/monitoring",
    ]
  }
}

provider "kubernetes" {
  #version = "1.11.2"
  load_config_file = "false"
  host = google_container_cluster.primary.endpoint

  username               = google_container_cluster.primary.master_auth[0].username
  password               = google_container_cluster.primary.master_auth[0].password
  client_certificate     = base64decode(google_container_cluster.primary.master_auth[0].client_certificate)
  client_key             = base64decode(google_container_cluster.primary.master_auth[0].client_key)
  cluster_ca_certificate = base64decode(google_container_cluster.primary.master_auth[0].cluster_ca_certificate)
}

resource "kubernetes_namespace" "healthcare-dicom-adapter" {
  metadata {
    name = "namespace-dicom-import-adapter"
  }
}