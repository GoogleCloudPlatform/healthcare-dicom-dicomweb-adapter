# Healthcare processing
# create healthcare dataset for storing dicom files
resource "google_healthcare_dataset" "dataset" {
  name     = var.dataset
  location = var.region
}

# create healthcare store for storing dicom files
resource "google_healthcare_dicom_store" "default" {
  name    = var.store
  dataset = google_healthcare_dataset.dataset.id

  labels = {
    label1 = "healthcare-label"
  }
}