# Notifications Service - Testing & Deployment Guide

## Testing

### Unit Tests
Run controller and service unit tests (no DB required):
```bash
mvn clean test
```

### Integration Tests (Postgres + Testcontainers)
Requires Docker. Run repository/migration tests:
```bash
# With Docker running
mvn test -Dtest=NotificationRepositoryIT
```

### Email Integration Tests (LocalStack)
Requires Docker. Test email delivery via SMTP:
```bash
# With Docker running
mvn test -Dtest=EmailServiceIT
```

### Full Build
```bash
mvn clean install
```

## Local Development

### Start Dependencies
```bash
docker compose up -d postgres phppgadmin
```

- Postgres: `localhost:5432` (user: `dopaminelite`, password: `dopaminepass`, db: `dopaminelite_notifications`)
- phpPgAdmin: http://localhost:8081

### Run Application
```bash
./mvnw spring-boot:run
```

Endpoints:
- Health: http://localhost:8080/actuator/health
- API: http://localhost:8080/api/v1/notifications

## Deployment (AWS ECS)

### Prerequisites
1. **AWS Resources**:
   - ECR repository: `notifications`
   - ECS cluster: `notifications-cluster`
   - ECS service: `notifications-service`
   - RDS Postgres instance
   - IAM roles: `ecsTaskExecutionRole`, `notificationsTaskRole`
   - CloudWatch log group: `/ecs/notifications`
   - SES verified sender identity

2. **AWS SSM Parameters** (for CI/CD):
   - `/notifications/DB_URL`
   - `/notifications/DB_USERNAME`
   - `/notifications/DB_PASSWORD`
   - `/notifications/EMAIL_SENDER`
   - `/notifications/EMAIL_REGION`

3. **GitHub Secrets**:
   - `AWS_ACCOUNT_ID`
   - `AWS_REGION`
   - `AWS_ACCESS_KEY_ID` + `AWS_SECRET_ACCESS_KEY` or `AWS_ROLE_TO_ASSUME`
   - `ECR_REPOSITORY`

### Deployment Flow
Push to `main` branch triggers GitHub Actions workflow:
1. Build and test code
2. Build Docker image
3. Push to ECR
4. Fetch secrets from SSM
5. Run Liquibase migration (pre-deploy)
6. Register ECS task definition
7. Update ECS service

### Manual Deployment
```bash
# Build image
docker build -t notifications .

# Tag and push to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com
docker tag notifications:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/notifications:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/notifications:latest

# Update ECS service
aws ecs update-service --cluster notifications-cluster --service notifications-service --force-new-deployment
```

### Rollback
```bash
# Revert to previous task definition revision
aws ecs update-service \
  --cluster notifications-cluster \
  --service notifications-service \
  --task-definition notifications-task:<previous-revision>
```

## Database Migrations

Liquibase runs automatically at startup. For manual control:
```bash
./mvnw liquibase:update -Dspring.datasource.url=<jdbc-url>
./mvnw liquibase:status
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1
```

## Monitoring

- CloudWatch Logs: `/ecs/notifications`
- Health check: `GET /actuator/health`
- Metrics: Enable CloudWatch Container Insights on ECS cluster

## Troubleshooting

**Build fails with DB connection error**: Disable `NotificationsApplicationTests` or start Postgres locally.

**Integration tests fail**: Ensure Docker is running and accessible.

**ECS tasks unhealthy**: Check CloudWatch logs, verify DB connectivity, confirm SES sender is verified.

**Deployment stuck**: Check task definition environment variables match SSM parameters; verify IAM roles.
