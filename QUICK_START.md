# LeetSync Backend V2 - Quick Start Guide

## 🚀 Getting Started in 5 Minutes

### Step 1: Prerequisites
- Java 21 or higher
- GitHub Personal Access Token (with `repo` scope)
- Maven (included via wrapper)

### Step 2: Generate GitHub Token
1. Go to https://github.com/settings/tokens
2. Click "Generate new token" → "Generate new token (classic)"
3. Give it a name: `LeetSync`
4. Check scope: `repo`
5. Copy the token (you won't see it again!)

### Step 3: Set Environment Variables

**Windows PowerShell:**
```powershell
$env:GITHUB_TOKEN = "ghp_your_token_here"
$env:GITHUB_USERNAME = "your_github_username"
$env:GITHUB_REPOSITORY = "leetcode"
```

**Linux/macOS (bash):**
```bash
export GITHUB_TOKEN="ghp_your_token_here"
export GITHUB_USERNAME="your_github_username"
export GITHUB_REPOSITORY="leetcode"
```

### Step 4: Build & Run

```bash
cd leetsync-backend

# Build
./mvnw clean package -DskipTests

# Run
java -jar target/leetsync-backend-0.0.1-SNAPSHOT.jar
```

### Step 5: Verify

Open in browser or use curl:
```bash
curl http://localhost:8080/api/health
```

Expected response:
```json
{
  "status": "UP",
  "service": "LeetSync Backend"
}
```

---

## 🔌 API Endpoints

### Health Check
```bash
GET http://localhost:8080/api/health
```

### Check GitHub Repository
```bash
GET http://localhost:8080/api/submissions/github
```

### Setup GitHub Repository
```bash
GET http://localhost:8080/api/submissions/github/setup
```

### Submit Solution
```bash
POST http://localhost:8080/api/submissions
Content-Type: application/json

{
  "problemNo": 144,
  "problemTitle": "Binary Tree Preorder Traversal",
  "language": "java",
  "code": "public class Solution { ... }",
  "status": "Accepted"
}
```

---

## 📚 Response Examples

### Success Response (HTTP 200)
```json
{
  "success": true,
  "message": "Solution synced successfully",
  "problemNo": 144,
  "problemTitle": "Binary Tree Preorder Traversal",
  "language": "java"
}
```

### Validation Error (HTTP 400)
```json
{
  "success": false,
  "message": "Validation failed: Problem number must be greater than 0",
  "errorCode": "VALIDATION_ERROR",
  "timestamp": "2026-09-14T10:06:29Z"
}
```

### GitHub Error (HTTP 502)
```json
{
  "success": false,
  "message": "GitHub authentication failed. Check your token.",
  "errorCode": "GITHUB_AUTH_FAILED",
  "timestamp": "2026-09-14T10:06:29Z"
}
```

---

## 🧪 Testing with curl

### Test Health Endpoint
```bash
curl -X GET http://localhost:8080/api/health
```

### Test Submission (Invalid Status)
```bash
curl -X POST http://localhost:8080/api/submissions \
  -H "Content-Type: application/json" \
  -d '{
    "problemNo": 1,
    "problemTitle": "Two Sum",
    "language": "java",
    "code": "class Solution {}",
    "status": "Wrong Answer"
  }'
```

Expected: HTTP 400 with validation error

### Test GitHub Setup
```bash
curl -X GET http://localhost:8080/api/submissions/github/setup
```

---

## 🔍 Logging

View application logs for debugging:

```bash
# Windows PowerShell - Tail logs
Get-Content -Path target\leetsync-backend.log -Tail 50 -Wait

# Linux/macOS - Tail logs
tail -f target/leetsync-backend.log
```

### Log Levels

Set in `application.properties`:
```properties
logging.level.com.leetsync=DEBUG      # Detailed logging
logging.level.org.springframework.web=INFO
```

---

## 🐛 Troubleshooting

### Port 8080 Already in Use
```bash
# Run on different port
java -jar target/leetsync-backend-0.0.1-SNAPSHOT.jar --server.port=8081
```

### GitHub Token Invalid
```
Error: GitHub authentication failed
Solution: Check token at https://github.com/settings/tokens
```

### Connection Timeout
```
Error: Failed to access GitHub repository
Solution: Check internet connection and GitHub API status
```

### Missing Environment Variables
```
Error: GitHub token is required (github.token)
Solution: Set GITHUB_TOKEN environment variable
```

---

## 📖 Documentation

- **Full Implementation Summary:** `V2_IMPLEMENTATION_SUMMARY.md`
- **Swagger API Docs:** http://localhost:8080/swagger-ui.html
- **Source Code:** `src/main/java/com/leetsync/`

---

## 🔐 Security Notes

✅ **Never hardcode GitHub token in code**  
✅ **Always use environment variables**  
✅ **Keep token private - don't share in repos**  
✅ **Rotate token periodically**  

---

## 🎯 Next Steps

1. ✅ Backend is running
2. ✅ Chrome Extension can connect
3. ✅ Start syncing LeetCode solutions to GitHub!

Enjoy LeetSync! 🚀
