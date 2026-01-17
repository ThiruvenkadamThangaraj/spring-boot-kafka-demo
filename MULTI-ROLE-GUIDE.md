# Multi-Role Authentication Guide

## 📋 Overview
Your application now supports multiple user roles with hierarchical permissions:
- **ADMIN**: Full system access
- **OPERATOR**: Operations and processing
- **REVIEWER**: Review and approval workflows
- **USER**: Basic access

## 🔐 Demo Users

| Username | Password | Roles |
|----------|----------|-------|
| `admin` | `admin123` | ADMIN, OPERATOR, REVIEWER, USER |
| `operator` | `operator123` | OPERATOR, REVIEWER, USER |
| `reviewer` | `reviewer123` | REVIEWER, USER |
| `user` | `user123` | USER |

## 🎯 Role-Based Endpoints

### 1. Admin Only Endpoints
```
GET    /api/admin/dashboard           # Admin dashboard
DELETE /api/admin/users/{id}          # Delete user
POST   /api/admin/settings            # System settings
```

**Required Role:** `ADMIN`

### 2. Operator Endpoints
```
GET    /api/operator/dashboard        # Operator dashboard
POST   /api/operator/evaluation/{id}/process  # Process evaluation
PUT    /api/operator/sampling/{id}/approve    # Approve sampling
```

**Required Roles:** `OPERATOR` or `ADMIN`

### 3. Reviewer Endpoints
```
GET    /api/reviewer/dashboard        # Reviewer dashboard
POST   /api/reviewer/items/{id}/review        # Review item
GET    /api/reviewer/pending-reviews          # List pending reviews
```

**Required Roles:** `REVIEWER`, `OPERATOR`, or `ADMIN`

### 4. User Endpoints
```
GET    /api/users/profile             # User profile
GET    /api/users/my-items            # User's items
```

**Required Roles:** Any authenticated user (USER, REVIEWER, OPERATOR, ADMIN)

## 🧪 Testing Examples

### 1. Login as Admin
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER"],
  "expiresIn": 86400000
}
```

### 2. Login as Operator
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "operator",
    "password": "operator123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "operator",
  "roles": ["ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER"],
  "expiresIn": 86400000
}
```

### 3. Login as Reviewer
```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "reviewer",
    "password": "reviewer123"
  }'
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "type": "Bearer",
  "username": "reviewer",
  "roles": ["ROLE_REVIEWER", "ROLE_USER"],
  "expiresIn": 86400000
}
```

### 4. Access Admin Endpoint (Admin User)
```bash
curl -H "Authorization: Bearer <admin-token>" \
  http://localhost:8081/api/admin/dashboard
```

**Response:** ✅ 200 OK
```json
{
  "message": "Welcome to Admin Dashboard",
  "username": "admin",
  "roles": ["ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER"],
  "capabilities": [
    "Manage all users",
    "Configure system settings",
    "View all reports",
    "Perform all operations"
  ]
}
```

### 5. Access Admin Endpoint (Operator User)
```bash
curl -H "Authorization: Bearer <operator-token>" \
  http://localhost:8081/api/admin/dashboard
```

**Response:** ❌ 403 Forbidden
```json
{
  "timestamp": "2026-01-13T10:30:45.123+00:00",
  "status": 403,
  "error": "Forbidden",
  "message": "Access Denied",
  "path": "/api/admin/dashboard"
}
```

### 6. Access Operator Endpoint (Operator User)
```bash
curl -H "Authorization: Bearer <operator-token>" \
  http://localhost:8081/api/operator/dashboard
```

**Response:** ✅ 200 OK
```json
{
  "message": "Welcome to Operator Dashboard",
  "username": "operator",
  "roles": ["ROLE_OPERATOR", "ROLE_REVIEWER", "ROLE_USER"],
  "capabilities": [
    "Process evaluations",
    "Manage sampling operations",
    "Update evidence records",
    "Review and approve items"
  ]
}
```

### 7. Process Evaluation (Operator)
```bash
curl -X POST \
  -H "Authorization: Bearer <operator-token>" \
  http://localhost:8081/api/operator/evaluation/123/process
```

**Response:** ✅ 200 OK
```json
{
  "message": "Evaluation processed successfully",
  "evaluationId": 123,
  "processedBy": "operator",
  "status": "PROCESSED"
}
```

### 8. Review Item (Reviewer)
```bash
curl -X POST \
  -H "Authorization: Bearer <reviewer-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "APPROVED",
    "comments": "Looks good, approved"
  }' \
  http://localhost:8081/api/reviewer/items/456/review
```

**Response:** ✅ 200 OK
```json
{
  "message": "Item reviewed successfully",
  "itemId": 456,
  "reviewedBy": "reviewer",
  "status": "APPROVED",
  "comments": "Looks good, approved"
}
```

### 9. Access Reviewer Endpoint (Regular User)
```bash
curl -H "Authorization: Bearer <user-token>" \
  http://localhost:8081/api/reviewer/dashboard
```

**Response:** ❌ 403 Forbidden

### 10. Access User Endpoint (Any Authenticated User)
```bash
curl -H "Authorization: Bearer <any-token>" \
  http://localhost:8081/api/users/profile
```

**Response:** ✅ 200 OK (works for all authenticated users)

## 🔒 Method-Level Security

You can also use `@PreAuthorize` annotations on service methods:

```java
@Service
public class UserService {
    
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(Long userId) {
        // Only ADMIN can call this
    }
    
    @PreAuthorize("hasAnyRole('OPERATOR', 'ADMIN')")
    public void processEvaluation(Long evalId) {
        // OPERATOR or ADMIN can call this
    }
    
    @PreAuthorize("hasRole('REVIEWER') or #username == authentication.name")
    public User getUser(String username) {
        // REVIEWER can access any user, or users can access themselves
    }
    
    @PreAuthorize("isAuthenticated()")
    public List<Item> getMyItems() {
        // Any authenticated user
    }
}
```

## 📊 Role Hierarchy

```
ADMIN
  ├─ All OPERATOR permissions
  │   ├─ All REVIEWER permissions
  │   │   ├─ All USER permissions
  │   │   │   └─ Basic access
  │   │   └─ Review & approve
  │   └─ Process & manage operations
  └─ System administration
```

## 🎨 Adding New Roles

### Step 1: Update AuthController
```java
users.put("manager", new UserCredentials(
    passwordEncoder.encode("manager123"),
    Arrays.asList("ROLE_MANAGER", "ROLE_REVIEWER", "ROLE_USER")
));
```

### Step 2: Update SecurityConfig
```java
.requestMatchers("/api/manager/**").hasAnyRole("MANAGER", "ADMIN")
```

### Step 3: Add Controller Methods
```java
@GetMapping("/api/manager/reports")
@PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
public ResponseEntity<?> getReports() {
    // Manager-specific logic
}
```

## 🗄️ Production Implementation (Database-based)

For production, replace hardcoded users with database:

```java
@Entity
public class User {
    @Id
    private Long id;
    private String username;
    private String password;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles;
}

@Entity
public class Role {
    @Id
    private Long id;
    private String name; // ROLE_ADMIN, ROLE_OPERATOR, etc.
}

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    @Autowired
    private UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPassword())
            .authorities(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList()))
            .build();
    }
}
```

## 🧪 Test All Roles Script

```powershell
# Save as test-roles.ps1

# Login as each user and test their permissions
$users = @(
    @{username="admin"; password="admin123"},
    @{username="operator"; password="operator123"},
    @{username="reviewer"; password="reviewer123"},
    @{username="user"; password="user123"}
)

foreach ($user in $users) {
    Write-Host "`n========== Testing $($user.username) ==========" -ForegroundColor Cyan
    
    # Login
    $response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{username=$user.username; password=$user.password} | ConvertTo-Json)
    
    $token = $response.token
    Write-Host "Token: $($token.Substring(0, 20))..." -ForegroundColor Green
    Write-Host "Roles: $($response.roles -join ', ')" -ForegroundColor Yellow
    
    # Test different endpoints
    $endpoints = @(
        "/api/admin/dashboard",
        "/api/operator/dashboard",
        "/api/reviewer/dashboard",
        "/api/users/profile"
    )
    
    foreach ($endpoint in $endpoints) {
        try {
            $result = Invoke-RestMethod -Uri "http://localhost:8081$endpoint" `
                -Method GET `
                -Headers @{Authorization="Bearer $token"}
            Write-Host "✅ $endpoint - Success" -ForegroundColor Green
        } catch {
            Write-Host "❌ $endpoint - Access Denied" -ForegroundColor Red
        }
    }
}
```

## 📝 Summary

Your application now supports:
- ✅ Multiple user roles (ADMIN, OPERATOR, REVIEWER, USER)
- ✅ Role-based endpoint access control
- ✅ Method-level security with @PreAuthorize
- ✅ Hierarchical role permissions
- ✅ JWT tokens containing multiple roles
- ✅ Demo users for testing all roles
- ✅ Example controllers showing role usage
