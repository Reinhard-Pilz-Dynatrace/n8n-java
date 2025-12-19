# Kubernetes Manifests for E-Commerce Workshop Demo

## Quick Deploy

```bash
# Apply all manifests
kubectl apply -f k8s/

# Or apply individually
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
```

## Verify Deployment

```bash
# Check pods
kubectl get pods -n workshop

# Check service
kubectl get svc -n workshop

# View logs
kubectl logs -n workshop -l app=ecommerce-workshop -f

# Port forward for local access
kubectl port-forward -n workshop svc/ecommerce-workshop 8080:8080
```

## Access the Application

Once port-forwarded:
- Orders API: http://localhost:8080/orders?customerId=1
- Health check: http://localhost:8080/health
- Shutdown: `curl -X POST http://localhost:8080/shutdown`

## Update Image Version

Edit `deployment.yaml` and change the image tag:
```yaml
image: YOUR_DOCKERHUB_USERNAME/ecommerce-workshop:v1.0.3
```

Then apply:
```bash
kubectl apply -f k8s/deployment.yaml
```

## Configuration

### Resources
- Memory: 512Mi request, 1Gi limit
- CPU: 250m request, 1000m limit
- JVM: -Xmx768m -Xms512m

### Health Checks
- Liveness: `/health` every 10s after 30s
- Readiness: `/health` every 5s after 10s

### Replicas
Currently set to 1 (single pod for consistent demo behavior)

## Dynatrace Integration

The application will be automatically instrumented by Dynatrace OneAgent if deployed in a monitored namespace.

Expected findings:
- N+1 query pattern (~1001 queries per request)
- Connection leak (~5% of requests)
- High database response time
- Memory pressure (unclosed connections)
- Pool exhaustion after ~6-7 minutes

## Notes

- ⚠️ **Update `YOUR_DOCKERHUB_USERNAME`** in deployment.yaml before applying
- Single replica recommended for predictable workshop behavior
- H2 in-memory database resets on pod restart (by design)
- Load generator runs automatically on startup
