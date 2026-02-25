# Deployment Guide - Hashtag Local Backend on GCP VM

This guide walks through deploying the Hashtag Local Backend Java application on a GCP Compute Engine VM instance.

## Prerequisites

- GCP VM instance provisioned: `hashtaglocalbackend` in `us-central1-f` zone
- SSH access configured with gcloud CLI
- PostgreSQL 18 with PostGIS 3.6 extension available or installable on VM
- GCS service account key (`gcs-key.json`) for Google Cloud Storage access

---

## Step 1: SSH into the VM Instance


Prod
```bash
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f
```

Stating
```bash
gcloud compute ssh ai-agent-staging --zone=us-central1-c
```

---

## Step 2: Update System and Install Java

```bash
# Update package manager
sudo apt-get update
sudo apt-get upgrade -y

# Install Java 25 (or the version specified in .tool-versions)
sudo apt-get install -y openjdk-25-jdk

# Verify Java installation
java -version
```

---

## Step 3: Obtain Cloud SQL Connection Details


Create database with PostGIS extension and store the creds.

Since you're using Cloud SQL, retrieve your connection information:


---

## Step 4: Install Git and Clone the Repository


### Using SSH Keys

```bash
# Install Git
sudo apt-get install -y git

# Generate SSH key on the VM
ssh-keygen -t ed25519 -C "admin@madhyamakist.com"
# Press Enter to accept default location, add passphrase if desired

# Display the public key
cat ~/.ssh/id_ed25519.pub

# Add this public key to GitHub:
# 1. Copy the output
# 2. Go to GitHub.com → Settings → SSH and GPG keys → New SSH key
# 3. Paste the key and save

# Test the connection
ssh -T git@github.com

# Clone the repository
cd /opt
sudo git clone git@github.com:smallTechOrg/hashtaglocal-backend.git
cd hashtaglocal-backend
```



---

## Step 5: Set Up Google Cloud Storage Credentials

```bash
# Copy the GCS key from your local machine to the VM
gcloud compute scp gcs-key.json hashtaglocalbackend:/tmp/gcs-key.json --zone=us-central1-f

# On the VM, move it to a secure location
sudo mv /tmp/gcs-key.json /opt/hashtaglocal-backend/gcs-key.json
sudo chown root:root /opt/hashtaglocal-backend/gcs-key.json
chmod 600 /opt/hashtaglocal-backend/gcs-key.json
```

---

## Step 6: Configure Application Properties (Simple .env Approach)

Create a `.env` file with your production credentials:

```bash
cat > /opt/hashtaglocal-backend/.env <<'EOF'
# Database Configuration - Use separate fields for special character passwords
SPRING_DATASOURCE_URL=jdbc:postgresql://10.110.112.7:5432/hashtaglocal
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=your_raw_password

# Google Cloud Configuration
GOOGLE_APPLICATION_CREDENTIALS=/opt/hashtaglocal-backend/gcs-key.json
EOF
```

**Replace the placeholders:**
- `YOUR_CLOUD_SQL_IP` - Your Cloud SQL instance IP address
- `postgres` - Your database username
- `your_raw_password` - Your **raw, unencoded** password (e.g., `?Ov~=[U\H}H-9lB`)

**Important for passwords with special characters:**
- Use **separate** `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` variables
- Use the **raw password** (not URL-encoded) in `SPRING_DATASOURCE_PASSWORD`
- Spring Boot will handle the encoding automatically when connecting
- Do NOT include credentials in the JDBC URL when using separate username/password variables

**Note:** These environment variables override the defaults in `application.yaml`.

---

## Step 7: Transfer the Application JAR

**On your local machine:**

```bash
# Build the JAR locally (if not already built)
./gradlew clean build

# Transfer JAR to VM (upload to /tmp first due to permissions)
gcloud compute scp build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar \
  hashtaglocalbackend:/tmp/hashtaglocal-backend.jar \
  --zone=us-central1-f

# Move to final location with sudo
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="sudo mv /tmp/hashtaglocal-backend.jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar"
```

---

## Step 8: Run the Application

### Option 1: Run in Foreground (for testing)

```bash
# Load environment variables from .env file
cd /opt/hashtaglocal-backend
set -a; source .env; set +a

# Run the application
java -jar build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar
```

### Option 2: Run as a Systemd Service (Recommended)

Create the systemd service file that loads your .env file:

```bash
sudo tee /etc/systemd/system/hashtaglocal-backend.service > /dev/null <<'EOF'
[Unit]
Description=Hashtag Local Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/hashtaglocal-backend
EnvironmentFile=/opt/hashtaglocal-backend/.env
ExecStart=/usr/bin/java -jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF
```

**Note:** Replace `your-user` with your actual username (e.g., `echo $USER` to find it).

Enable and start the service:

```bash
sudo systemctl daemon-reload
sudo systemctl enable hashtaglocal-backend
sudo systemctl start hashtaglocal-backend
sudo systemctl restart hashtaglocal-backend

# Check status
sudo systemctl status hashtaglocal-backend

# View logs
sudo journalctl -u hashtaglocal-backend -f
```

---

## Step 9: Verify the Application is Running

```bash
# Check application health endpoint
curl http://localhost:8080/actuator/health

# Check OpenAPI docs (if not in firewall-restricted environment)
curl http://localhost:8080/v1/api-docs
```

---

## Step 10: Configure Firewall and Load Balancer

For production setup with load balancer integration, see [infrastructure.md](infrastructure.md) for complete instructions on:
- Firewall rules for health checks
- Instance group configuration
- Backend service setup
- Load balancer path routing for `/local/*`
- SSL/TLS configuration
- Monitoring and troubleshooting

**Quick verification after infrastructure setup:**

```bash
# Test application is accessible via load balancer
curl https://yourdomain.com/local/actuator/health

# Check backend health
gcloud compute backend-services get-health hashtaglocal-backend --global
```

---

## Additional Commands

### Stop the Application

```bash
sudo systemctl stop hashtaglocal-backend
```

### Restart the Application

```bash
sudo systemctl restart hashtaglocal-backend
```

### View Recent Logs

```bash
sudo journalctl -u hashtaglocal-backend -n 100
```

### Redeploy (after code changes)

**Recommended: Build locally and transfer JAR (Fastest)**

```bash
# On local machine
./gradlew clean build

# Transfer JAR to VM (to /tmp first)
gcloud compute scp build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar \
  hashtaglocalbackend:/tmp/hashtaglocal-backend.jar \
  --zone=us-central1-f

# Move to final location and restart service
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="sudo mv /tmp/hashtaglocal-backend.jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar && sudo systemctl restart hashtaglocal-backend"

# Check logs
gcloud compute ssh hashtaglocalbackend --zone=us-central1-f \
  --command="sudo journalctl -u hashtaglocal-backend -f"
```


## Staging

```bash
gcloud compute scp build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar ai-agent-staging:/tmp/hashtaglocal-backend.jar --zone=us-central1-c

gcloud compute ssh ai-agent-staging --zone=us-central1-c --command="sudo mv /tmp/hashtaglocal-backend.jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar && sudo systemctl restart hashtaglocal-backend" 

gcloud compute scp gcs-key.json \
  ai-agent-staging:/tmp/gcs-key.json \
  --zone=us-central1-c
```




# VM Commands 

For memory
```
free | grep Mem | awk '{print $3/$2 * 100.0"%"}'
df -h
```

### Database Connection (from VM)

```bash
# Connect to Cloud SQL using gcloud CLI
gcloud sql connect INSTANCE_NAME --user=hashtaglocaluser

# Or, if you've installed cloud_sql_proxy:
./cloud_sql_proxy -instances=PROJECT:us-central1:INSTANCE_NAME=tcp:5432
# Then in another terminal:
psql -U hashtaglocaluser -d hashtaglocal -h localhost
```

---

## Troubleshooting

### Java Version Mismatch
- Ensure Java 25 is installed. Check with `java -version`
- Update `JAVA_HOME` if needed: `export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`

### Database Connection Issues
- Verify Cloud SQL instance is running in GCP Console
- Check database credentials in `application-prod.yaml` match Cloud SQL user
- Ensure VM has network connectivity to Cloud SQL (check firewall rules and Cloud SQL Authorized Networks)
- Verify the Cloud SQL IP address is correct: `gcloud sql instances describe INSTANCE_NAME --format='value(ipAddresses[0].ipAddress)'`
- If using private IP, ensure VPC peering/connectivity is configured

### GCS Credentials Not Found
- Verify `GOOGLE_APPLICATION_CREDENTIALS` environment variable is set
- Check file permissions: `ls -la /opt/hashtaglocal-backend/gcs-key.json`
- Ensure the service account has necessary permissions in GCP Console

### Application Won't Start
- Check logs: `sudo journalctl -u hashtaglocal-backend -f`
- Verify port 8080 is not in use: `sudo netstat -tlnp | grep 8080`
- Check system resources: `free -h` (RAM availability)

### Rebuild and Redeploy
```bash
cd /opt/hashtaglocal-backend
./gradlew clean build -Dorg.gradle.jvmargs="-Xmx1024m"
sudo systemctl restart hashtaglocal-backend
```

---

## Performance Tuning

### JVM Memory Settings
Adjust JVM heap size in the systemd service:

```ini
ExecStart=/usr/bin/java -Xmx512m -Xms256m -jar /opt/hashtaglocal-backend/build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar
```

### Database Connection Pool
Update `application-prod.yaml`:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 10000
      max-lifetime: 1800000
```

---

## Monitoring

Consider setting up monitoring via Google Cloud Monitoring or similar tools:

- Monitor CPU/Memory usage
- Track application logs
- Set up alerts for service failures
- Monitor database performance

---

## Next Steps

1. Set up SSL/TLS with a reverse proxy (nginx/Apache)
2. Configure automatic backups for PostgreSQL
3. Set up log aggregation (Cloud Logging, Stackdriver, etc.)
4. Implement health checks and auto-restart policies
5. Consider containerizing with Docker for easier deployment

---

## Rollback Procedure

If issues occur after deployment:

```bash
# Stop the service
sudo systemctl stop hashtaglocal-backend

# Revert to previous commit
cd /opt/hashtaglocal-backend
git log --oneline -5
git checkout <previous-commit-hash>

# Rebuild
./gradlew clean build

# Restart
sudo systemctl start hashtaglocal-backend
```

---

## Support & Documentation

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [PostgreSQL Documentation](https://www.postgresql.org/docs/)
- [Google Cloud Documentation](https://cloud.google.com/docs)
- [Project README](./README.md)
