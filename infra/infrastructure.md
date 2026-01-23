# Infrastructure Setup - Hashtag Local Backend

## What Was Done

✅ **Firewall**: Created `allow-hashtaglocal-lb-health-check` rule  
✅ **Instance Group**: Created `hashtaglocal-instance-group` in us-central1-f  
✅ **Health Check**: Created `hashtaglocal-health-check` on `/actuator/health:8080`  
✅ **Backend Service**: Created `hashtaglocal-backend` with EXTERNAL_MANAGED scheme  
✅ **Load Balancer**: Updated `https-staging` to route `/local/*` requests  
✅ **Path Rewrite**: Configured `/local` prefix to be stripped when forwarding to backend  

## Infrastructure Files

- `staging-urlmap-backup.yaml` - Original staging load balancer configuration  
- `staging-urlmap-updated.yaml` - Updated configuration with /local path routing  

## Testing

```bash
# Health check endpoint - WORKING ✅
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
```

## Backend Health Status

```bash
gcloud compute backend-services get-health hashtaglocal-backend --global
```

**Status**: HEALTHY ✅

## Architecture

```
Internet
  ↓
https://staging.api.smalltech.in
  ↓
[Global Load Balancer: https-staging]
  ↓
[URL Map Router]
  ├─ /local/* → [Path Rewrite: /local → /] → hashtaglocal-backend
  └─ /* → staging-backend (default)
  ↓
[Backend Service: hashtaglocal-backend]
  ↓
[Health Check: GET /actuator/health:8080]
  ↓
[Instance Group: hashtaglocal-instance-group]
  ↓
[VM: hashtaglocalbackend (us-central1-f)]
  ↓
[Spring Boot App: :8080]
```

## GCP Resources Created

| Resource | Name | Status |
|----------|------|--------|
| Firewall Rule | `allow-hashtaglocal-lb-health-check` | ✅ Active |
| Instance Group | `hashtaglocal-instance-group` | ✅ Active |
| Health Check | `hashtaglocal-health-check` | ✅ Active |
| Backend Service | `hashtaglocal-backend` | ✅ Active (HEALTHY) |
| URL Map | `https-staging` (updated) | ✅ Active |
| VM Instance | `hashtaglocalbackend` | ✅ Running |

## Configuration Files

**URL Map Path Rule:**
- Matches: `/local/` prefix
- Action: URL rewrite - strip `/local` prefix
- Backend: `hashtaglocal-backend`
- Weight: 100

**Backend Service:**
- Protocol: HTTP
- Port: 8080
- Health Check: `/actuator/health`
- Check Interval: 10 seconds
- Timeout: 30 seconds

## Quick Troubleshooting

**Check backend health:**
```bash
gcloud compute backend-services get-health hashtaglocal-backend --global
```

**Check application status on VM:**
```bash
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="sudo systemctl status hashtaglocal-backend"
```

**View application logs:**
```bash
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="sudo journalctl -u hashtaglocal-backend -f"
```

**Test endpoint on VM:**
```bash
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="curl -s http://localhost:8080/actuator/health | jq ."
```

