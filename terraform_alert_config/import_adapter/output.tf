output "lb_ip" {
  value = kubernetes_service.dicom-adapter.load_balancer_ingress[0].ip
}

data "google_monitoring_uptime_check_ips" "ips" {
}

output "ip_list" {
  value = data.google_monitoring_uptime_check_ips.ips.uptime_check_ips
}
