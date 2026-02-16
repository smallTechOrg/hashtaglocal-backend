# Infrastructure Setup - Hashtag Local Backend

## Overview

This document provides complete setup instructions for the Hashtag Local Backend infrastructure on Google Cloud Platform, including load balancer configuration, health checks, and backend services.

## Initial Setup - Complete Command Reference

### 1. Firewall Rule for Health Checks

Create a firewall rule to allow Google Cloud Load Balancer health checks to reach the backend instances:

```bash
# Create firewall rule for load balancer health checks
gcloud compute firewall-rules create allow-hashtaglocal-lb-health-check \
  --network=default \
  --action=ALLOW \
  --direction=INGRESS \
  --source-ranges=35.191.0.0/16,130.211.0.0/22 \
  --rules=tcp:8080 \
  --target-tags=hashtaglocal-backend \
  --description="Allow GCP Load Balancer health checks to hashtaglocal backend on port 8080"

# Verify firewall rule
gcloud compute firewall-rules describe allow-hashtaglocal-lb-health-check
```

**Note**: Source ranges `35.191.0.0/16` and `130.211.0.0/22` are Google Cloud's health check IP ranges.

### 2. Instance Group Setup

Create an unmanaged instance group to contain the backend VM:

```bash
# Create unmanaged instance group
gcloud compute instance-groups unmanaged create hashtaglocal-instance-group \
  --zone=us-central1-f \
  --description="Instance group for hashtaglocal backend"

# Add the VM instance to the group
gcloud compute instance-groups unmanaged add-instances hashtaglocal-instance-group \
  --zone=us-central1-f \
  --instances=hashtaglocalbackend

# Create unmanaged instance group
gcloud compute instance-groups unmanaged create hashtaglocal-staging-instance-group \
  --zone=us-central1-c \
  --description="Instance group for hashtaglocal staging backend"

# Add the VM instance to the group
gcloud compute instance-groups unmanaged add-instances hashtaglocal-staging-instance-group \
  --zone=us-central1-c \
  --instances=ai-agent-staging

# Set named port for the instance group
gcloud compute instance-groups unmanaged set-named-ports hashtaglocal-staging-instance-group \
  --zone=us-central1-c \
  --named-ports=http:8080

# Verify instance group
gcloud compute instance-groups unmanaged describe hashtaglocal-staging-instance-group \
  --zone=us-central1-c
```

### 3. Health Check Configuration

Create a health check to monitor the backend application:

```bash
# Create HTTP health check
gcloud compute health-checks create http hashtaglocal-health-check \
  --port=8080 \
  --request-path=/actuator/health \
  --check-interval=10s \
  --timeout=5s \
  --unhealthy-threshold=2 \
  --healthy-threshold=2 \
  --description="Health check for hashtaglocal backend Spring Boot actuator"

# Verify health check
gcloud compute health-checks describe hashtaglocal-health-check
```

### 4. Backend Service Creation

Create the backend service that connects the instance group with the load balancer:

```bash
# Create backend service
gcloud compute backend-services create hashtaglocal-backend \
  --protocol=HTTP \
  --port-name=http \
  --health-checks=hashtaglocal-health-check \
  --global \
  --load-balancing-scheme=EXTERNAL_MANAGED \
  --timeout=30s \
  --description="Backend service for hashtaglocal backend"

gcloud compute backend-services create hashtaglocal-staging-backend \
  --protocol=HTTP \
  --port-name=http \
  --health-checks=hashtaglocal-health-check \
  --global \
  --load-balancing-scheme=EXTERNAL_MANAGED \
  --timeout=30s \
  --description="Backend service for hashtaglocal staging backend"

# Add the instance group as a backend
gcloud compute backend-services add-backend hashtaglocal-backend \
  --instance-group=hashtaglocal-instance-group \
  --instance-group-zone=us-central1-f \
  --balancing-mode=UTILIZATION \
  --max-utilization=0.8 \
  --global


gcloud compute backend-services add-backend hashtaglocal-staging-backend \
  --instance-group=hashtaglocal-staging-instance-group \
  --instance-group-zone=us-central1-c \
  --balancing-mode=UTILIZATION \
  --max-utilization=0.8 \
  --global

# Verify backend service
gcloud compute backend-services describe hashtaglocal-backend --global

# Check backend health
gcloud compute backend-services get-health hashtaglocal-backend --global
```

### 5. Load Balancer URL Map Update

Update the existing load balancer URL map to route traffic to the new backend:

```bash
# Export current URL map for backup
gcloud compute url-maps export https-staging \
  --destination=staging-urlmap-backup.yaml \
  --global

# Create updated URL map configuration (staging-urlmap-updated.yaml)
# See the Configuration section below for the YAML structure

# Update the URL map
gcloud compute url-maps import https-staging \
  --source=staging-urlmap-updated.yaml \
  --global

# Verify URL map
gcloud compute url-maps describe https-staging --global
```

## Load Balancer Configuration Details

### URL Map Path Routing with Rewrite

The URL map is configured to route `/local/*` requests to the hashtaglocal backend while stripping the `/local` prefix:

```yaml
name: https-staging
defaultService: https://www.googleapis.com/compute/v1/projects/YOUR_PROJECT/global/backendServices/staging-backend
hostRules:
- hosts:
  - staging.api.smalltech.in
  pathMatcher: staging-matcher
pathMatchers:
- name: staging-matcher
  defaultService: https://www.googleapis.com/compute/v1/projects/YOUR_PROJECT/global/backendServices/staging-backend
  pathRules:
  - paths:
    - /local/*
    routeAction:
      urlRewrite:
        pathPrefixRewrite: /
    service: https://www.googleapis.com/compute/v1/projects/YOUR_PROJECT/global/backendServices/hashtaglocal-backend
```

### Applying URL Map Changes

```bash
# Update URL map from YAML file
gcloud compute url-maps import https-staging \
  --source=staging-urlmap-updated.yaml \
  --global

# Alternative: Update URL map using CLI commands
gcloud compute url-maps add-path-matcher https-staging \
  --path-matcher-name=staging-matcher \
  --default-service=staging-backend \
  --path-rules="/local/*=hashtaglocal-backend" \
  --global
```

## Modifying the Load Balancer

### Change Health Check Parameters

```bash
# Update health check interval
gcloud compute health-checks update http hashtaglocal-health-check \
  --check-interval=15s

# Update health check timeout
gcloud compute health-checks update http hashtaglocal-health-check \
  --timeout=10s

# Update health check path
gcloud compute health-checks update http hashtaglocal-health-check \
  --request-path=/actuator/health/liveness

# Update unhealthy threshold
gcloud compute health-checks update http hashtaglocal-health-check \
  --unhealthy-threshold=3

# Update healthy threshold
gcloud compute health-checks update http hashtaglocal-health-check \
  --healthy-threshold=2
```

### Modify Backend Service Settings

```bash
# Update backend service timeout
gcloud compute backend-services update hashtaglocal-backend \
  --timeout=60s \
  --global

# Update backend configuration
gcloud compute backend-services update-backend hashtaglocal-backend \
  --instance-group=hashtaglocal-instance-group \
  --instance-group-zone=us-central1-f \
  --balancing-mode=UTILIZATION \
  --max-utilization=0.9 \
  --global

# Enable connection draining
gcloud compute backend-services update hashtaglocal-backend \
  --connection-draining-timeout=300 \
  --global

# Enable Cloud CDN (if needed)
gcloud compute backend-services update hashtaglocal-backend \
  --enable-cdn \
  --global
```

### Add Additional Backend Instances

```bash
# Add new instance to instance group
gcloud compute instance-groups unmanaged add-instances hashtaglocal-instance-group \
  --zone=us-central1-f \
  --instances=hashtaglocal-backend-2,hashtaglocal-backend-3

# List instances in group
gcloud compute instance-groups unmanaged list-instances hashtaglocal-instance-group \
  --zone=us-central1-f
```

### Add New Path Routes

```bash
# Export current URL map
gcloud compute url-maps export https-staging \
  --destination=current-urlmap.yaml \
  --global

# Edit the YAML file to add new path rules
# Example: Add /api/v2/* route
# pathRules:
# - paths:
#   - /api/v2/*
#   service: https://www.googleapis.com/compute/v1/projects/YOUR_PROJECT/global/backendServices/api-v2-backend

# Import updated URL map
gcloud compute url-maps import https-staging \
  --source=current-urlmap.yaml \
  --global
```

### Change URL Rewrite Rules

```bash
# Export URL map
gcloud compute url-maps export https-staging \
  --destination=urlmap-for-edit.yaml \
  --global

# Edit the routeAction section in the YAML:
# routeAction:
#   urlRewrite:
#     pathPrefixRewrite: /api/v1    # Change to different prefix
#     # OR
#     pathTemplateRewrite: /new-path  # Use template rewrite

# Apply changes
gcloud compute url-maps import https-staging \
  --source=urlmap-for-edit.yaml \
  --global
```

### Update SSL Certificates

```bash
# List current SSL certificates
gcloud compute ssl-certificates list

# Create new managed SSL certificate
gcloud compute ssl-certificates create new-cert-name \
  --domains=staging.api.smalltech.in \
  --global

# Update HTTPS proxy to use new certificate
gcloud compute target-https-proxies update https-staging-proxy \
  --ssl-certificates=new-cert-name \
  --global
```

## Testing and Validation

### Test Health Check Endpoint

```bash
# Test via load balancer
curl https://staging.api.smalltech.in/local/actuator/health

# Expected response:
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "ping": {"status": "UP"},
    "ssl": {"status": "UP"}
  }
}

# Test directly on VM
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="curl -s http://localhost:8080/actuator/health | jq ."
```

### Check Backend Health Status

```bash
# Check backend service health
gcloud compute backend-services get-health hashtaglocal-backend --global

# Expected output shows HEALTHY status for all instances
```

### Monitor Load Balancer Logs

```bash
# Enable logging on backend service (if not enabled)
gcloud compute backend-services update hashtaglocal-backend \
  --enable-logging \
  --logging-sample-rate=1.0 \
  --global

# View logs in Cloud Logging
gcloud logging read "resource.type=http_load_balancer AND 
  resource.labels.backend_service_name=hashtaglocal-backend" \
  --limit=50 \
  --format=json
```

### Test Load Balancer Routing

```bash
# Test /local/* path routing
curl -v https://staging.api.smalltech.in/local/actuator/health

# Test path rewrite (should access /api/... on backend)
curl https://staging.api.smalltech.in/local/api/issues

# Test with different endpoints
curl https://staging.api.smalltech.in/local/api/localities
```

### Performance Testing

```bash
# Install apache-bench if needed
# macOS: brew install apache-bench
# Ubuntu: apt-get install apache2-utils

# Load test the endpoint
ab -n 1000 -c 10 https://staging.api.smalltech.in/local/actuator/health

# Monitor backend during load test
gcloud compute backend-services get-health hashtaglocal-backend --global
```

## Architecture

```
Internet
  ↓
https://staging.api.smalltech.in
  ↓
[Global Load Balancer: https-staging]
  ↓
[HTTPS Target Proxy + SSL Certificate]
  ↓
[URL Map Router: https-staging]
  ↓
[Path Matcher: staging-matcher]
  ├─ /local/* → [Path Rewrite: /local → /] → hashtaglocal-backend
  └─ /* → staging-backend (default)
  ↓
[Backend Service: hashtaglocal-backend]
  ├─ Protocol: HTTP
  ├─ Port: 8080
  ├─ Health Check: /actuator/health
  └─ Timeout: 30s
  ↓
[Health Check: hashtaglocal-health-check]
  ├─ Check Interval: 10s
  ├─ Timeout: 5s
  ├─ Healthy Threshold: 2
  └─ Unhealthy Threshold: 2
  ↓
[Instance Group: hashtaglocal-instance-group (us-central1-f)]
  ↓
[VM: hashtaglocalbackend]
  ├─ Network Tag: hashtaglocal-backend
  ├─ Firewall: allow-hashtaglocal-lb-health-check
  └─ Port: 8080
  ↓
[Spring Boot Application]
  ├─ /actuator/health
  ├─ /api/issues
  └─ /api/localities
```

## GCP Resources Summary

| Resource Type | Name | Details | Status |
|---------------|------|---------|--------|
| Firewall Rule | `allow-hashtaglocal-lb-health-check` | TCP:8080, Source: GCP LB ranges | ✅ Active |
| Instance Group | `hashtaglocal-instance-group` | Zone: us-central1-f, Named port: http:8080 | ✅ Active |
| Health Check | `hashtaglocal-health-check` | HTTP:8080, Path: /actuator/health | ✅ Active |
| Backend Service | `hashtaglocal-backend` | Global, HTTP, Port: 8080 | ✅ Active |
| URL Map | `https-staging` | Path matcher with /local/* routing | ✅ Active |
| VM Instance | `hashtaglocalbackend` | us-central1-f, Tag: hashtaglocal-backend | ✅ Running |

## Troubleshooting Commands

### Diagnose Backend Issues

```bash
# Check overall backend health
gcloud compute backend-services get-health hashtaglocal-backend --global

# Describe backend service configuration
gcloud compute backend-services describe hashtaglocal-backend --global

# Check health check configuration
gcloud compute health-checks describe hashtaglocal-health-check

# List all backends in the service
gcloud compute backend-services describe hashtaglocal-backend \
  --global \
  --format="get(backends)"
```

### Check Application Status

```bash
# SSH into VM and check application
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f

# Once connected, run:
sudo systemctl status hashtaglocal-backend
sudo journalctl -u hashtaglocal-backend -n 100 --no-pager
curl http://localhost:8080/actuator/health
```

### Debug Firewall Issues

```bash
# List firewall rules affecting the instance
gcloud compute firewall-rules list \
  --filter="targetTags:hashtaglocal-backend" \
  --format="table(name,sourceRanges,allowed)"

# Check if health check firewall rule is active
gcloud compute firewall-rules describe allow-hashtaglocal-lb-health-check

# Test connectivity from health check IP ranges
# Note: This requires a test VM in the same network
```

### URL Map Debugging

```bash
# Get full URL map configuration
gcloud compute url-maps describe https-staging --global

# Export for detailed inspection
gcloud compute url-maps export https-staging \
  --destination=current-urlmap.yaml \
  --global

# Check all path matchers
gcloud compute url-maps describe https-staging \
  --global \
  --format="get(pathMatchers)"

# List all backend services referenced
gcloud compute url-maps describe https-staging \
  --global \
  --format="get(pathMatchers[].pathRules[].service)"
```

### Load Balancer Component Checks

```bash
# List all components of the load balancer
gcloud compute url-maps list
gcloud compute target-https-proxies list
gcloud compute ssl-certificates list
gcloud compute forwarding-rules list --global

# Check HTTPS proxy configuration
gcloud compute target-https-proxies describe https-staging-proxy --global

# Check forwarding rule
gcloud compute forwarding-rules describe https-staging-rule --global
```

## Common Modification Scenarios

### Scenario 1: Scale to Multiple Backend Instances

```bash
# 1. Create additional VM instances (same configuration as hashtaglocalbackend)
gcloud compute instances create hashtaglocal-backend-2 \
  --zone=us-central1-f \
  --machine-type=e2-medium \
  --tags=hashtaglocal-backend \
  --image-family=debian-11 \
  --image-project=debian-cloud

# 2. Add new instances to the instance group
gcloud compute instance-groups unmanaged add-instances hashtaglocal-instance-group \
  --zone=us-central1-f \
  --instances=hashtaglocal-backend-2

# 3. Verify instances are added
gcloud compute instance-groups unmanaged list-instances hashtaglocal-instance-group \
  --zone=us-central1-f

# 4. Check health of all backends
gcloud compute backend-services get-health hashtaglocal-backend --global
```

### Scenario 2: Change to a Different Port

```bash
# 1. Update named port in instance group
gcloud compute instance-groups unmanaged set-named-ports hashtaglocal-instance-group \
  --zone=us-central1-f \
  --named-ports=http:9090

# 2. Update health check to use new port
gcloud compute health-checks update http hashtaglocal-health-check \
  --port=9090

# 3. Update firewall rule
gcloud compute firewall-rules update allow-hashtaglocal-lb-health-check \
  --rules=tcp:9090

# 4. Verify backend health after changes
gcloud compute backend-services get-health hashtaglocal-backend --global
```

### Scenario 3: Add HTTPS Backend Support

```bash
# 1. Create HTTPS health check
gcloud compute health-checks create https hashtaglocal-health-check-https \
  --port=8443 \
  --request-path=/actuator/health

# 2. Update backend service to use HTTPS
gcloud compute backend-services update hashtaglocal-backend \
  --protocol=HTTPS \
  --health-checks=hashtaglocal-health-check-https \
  --global

# 3. Update firewall rule for HTTPS port
gcloud compute firewall-rules create allow-hashtaglocal-lb-https \
  --network=default \
  --action=ALLOW \
  --direction=INGRESS \
  --source-ranges=35.191.0.0/16,130.211.0.0/22 \
  --rules=tcp:8443 \
  --target-tags=hashtaglocal-backend
```

### Scenario 4: Add Request/Response Headers

```bash
# Export current URL map
gcloud compute url-maps export https-staging \
  --destination=urlmap-with-headers.yaml \
  --global

# Edit YAML to add header actions:
# pathRules:
# - paths:
#   - /local/*
#   routeAction:
#     urlRewrite:
#       pathPrefixRewrite: /
#     requestHeadersToAdd:
#     - headerName: X-Backend-Version
#       headerValue: v1.0
#       replace: true
#     responseHeadersToAdd:
#     - headerName: X-Served-By
#       headerValue: GCP-LB
#       replace: true
#   service: hashtaglocal-backend

# Apply updated URL map
gcloud compute url-maps import https-staging \
  --source=urlmap-with-headers.yaml \
  --global
```

### Scenario 5: Implement Traffic Splitting

```bash
# Export URL map
gcloud compute url-maps export https-staging \
  --destination=urlmap-traffic-split.yaml \
  --global

# Edit to add weighted backend services:
# pathRules:
# - paths:
#   - /local/*
#   routeAction:
#     urlRewrite:
#       pathPrefixRewrite: /
#     weightedBackendServices:
#     - backendService: hashtaglocal-backend
#       weight: 90
#     - backendService: hashtaglocal-backend-v2
#       weight: 10

# Apply changes
gcloud compute url-maps import https-staging \
  --source=urlmap-traffic-split.yaml \
  --global
```

## Monitoring and Alerts

### Enable Detailed Logging

```bash
# Enable backend service logging
gcloud compute backend-services update hashtaglocal-backend \
  --enable-logging \
  --logging-sample-rate=1.0 \
  --global

# Query recent logs
gcloud logging read "resource.type=http_load_balancer AND 
  resource.labels.backend_service_name=hashtaglocal-backend" \
  --limit=100 \
  --format=json \
  --freshness=1h
```

### Create Uptime Checks

```bash
# Create uptime check using gcloud (requires Cloud Monitoring API)
gcloud alpha monitoring uptime-checks create hashtaglocal-uptime \
  --display-name="Hashtag Local Backend Health" \
  --host=staging.api.smalltech.in \
  --path=/local/actuator/health \
  --protocol=https \
  --period=60s \
  --timeout=10s
```

### Set Up Alerts

```bash
# Create alert policy for backend health
gcloud alpha monitoring policies create \
  --notification-channels=CHANNEL_ID \
  --display-name="Hashtag Local Backend Unhealthy" \
  --condition-threshold-value=1 \
  --condition-threshold-duration=300s \
  --condition-display-name="Backend unhealthy for 5 minutes" \
  --condition-filter='resource.type="https_lb_rule" AND 
    resource.labels.backend_service_name="hashtaglocal-backend" AND 
    metric.type="loadbalancing.googleapis.com/https/backend_latencies"'
```

## Rollback Procedures

### Restore Previous URL Map

```bash
# If you have a backup YAML file
gcloud compute url-maps import https-staging \
  --source=staging-urlmap-backup.yaml \
  --global

# Verify rollback
gcloud compute url-maps describe https-staging --global
curl https://staging.api.smalltech.in/local/actuator/health
```

### Remove Backend from Load Balancer

```bash
# Remove backend from backend service
gcloud compute backend-services remove-backend hashtaglocal-backend \
  --instance-group=hashtaglocal-instance-group \
  --instance-group-zone=us-central1-f \
  --global

# Remove path rule from URL map (export, edit, import)
gcloud compute url-maps export https-staging \
  --destination=urlmap-no-local.yaml \
  --global

# Edit YAML to remove /local/* path rule, then:
gcloud compute url-maps import https-staging \
  --source=urlmap-no-local.yaml \
  --global
```

## Best Practices

1. **Always backup URL map** before making changes:
   ```bash
   gcloud compute url-maps export https-staging \
     --destination=backup-$(date +%Y%m%d-%H%M%S).yaml \
     --global
   ```

2. **Test health checks** directly on the instance before adding to load balancer:
   ```bash
   curl http://INSTANCE_IP:8080/actuator/health
   ```

3. **Monitor backend health** after any changes:
   ```bash
   watch -n 5 'gcloud compute backend-services get-health hashtaglocal-backend --global'
   ```

4. **Use connection draining** when removing backends:
   ```bash
   gcloud compute backend-services update hashtaglocal-backend \
     --connection-draining-timeout=300 \
     --global
   ```

5. **Enable logging** for troubleshooting:
   ```bash
   gcloud compute backend-services update hashtaglocal-backend \
     --enable-logging \
     --logging-sample-rate=0.5 \
     --global
   ```

## Quick Reference Commands

```bash
# View all load balancer components
gcloud compute url-maps list
gcloud compute backend-services list
gcloud compute health-checks list
gcloud compute instance-groups list

# Check backend health
gcloud compute backend-services get-health hashtaglocal-backend --global

# Test endpoint
curl https://staging.api.smalltech.in/local/actuator/health

# View logs
gcloud logging read "resource.type=http_load_balancer" --limit=50

# SSH to backend instance
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f

# Export URL map for editing
gcloud compute url-maps export https-staging --destination=urlmap.yaml --global

# Import updated URL map
gcloud compute url-maps import https-staging --source=urlmap.yaml --global
```

## Infrastructure Files

- `staging-urlmap-backup.yaml` - Original staging load balancer configuration  
- `staging-urlmap-updated.yaml` - Updated configuration with /local path routing  

## Current Status

✅ **Firewall**: `allow-hashtaglocal-lb-health-check` rule active  
✅ **Instance Group**: `hashtaglocal-instance-group` in us-central1-f  
✅ **Health Check**: `hashtaglocal-health-check` on `/actuator/health:8080`  
✅ **Backend Service**: `hashtaglocal-backend` with EXTERNAL_MANAGED scheme  
✅ **Load Balancer**: `https-staging` routing `/local/*` requests  
✅ **Path Rewrite**: `/local` prefix stripped when forwarding to backend  
✅ **Backend Health**: HEALTHY  

---

**Last Updated**: Setup completed and documented
**Endpoint**: https://staging.api.smalltech.in/local/actuator/health
**Status**: ✅ Production Ready

