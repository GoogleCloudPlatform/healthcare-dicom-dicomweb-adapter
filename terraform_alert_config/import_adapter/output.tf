output "lb_ip" {
  value = kubernetes_service.dicom-adapter.load_balancer_ingress[0].ip
}

data "google_monitoring_uptime_check_ips" "ips" {
}

