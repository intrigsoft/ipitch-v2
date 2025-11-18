# Keycloak Setup Guide for iPitch

This guide explains how to set up Keycloak authentication for the iPitch platform.

## Overview

The iPitch platform now uses Keycloak for authentication and authorization across all services:
- **User Manager** (Port 8084): Manages user profiles and authentication
- **Proposal Manager** (Port 8081): Manages proposals with authenticated users
- **Interaction Manager** (Port 8083): Manages comments and votes with authenticated users
- **Proposal View Manager** (Port 8082): Provides search and view functionality with authentication

## Starting Keycloak

Keycloak is included in the docker-compose.yml file and will start automatically:

```bash
docker-compose up -d
```

This will start:
- **Keycloak** on port 8080
- **Keycloak PostgreSQL Database** (separate from main application database)
- **iPitch PostgreSQL Database** on port 5432
- **Elasticsearch** on port 9200

## Accessing Keycloak Admin Console

1. Navigate to: http://localhost:8080
2. Click on "Administration Console"
3. Login with default credentials:
   - Username: `admin`
   - Password: `admin123`

## Initial Setup

### 1. Create Realm

1. Click on the realm dropdown (top left, says "Master")
2. Click "Create Realm"
3. Set realm name to: `ipitch`
4. Click "Create"

### 2. Create Client

1. In the `ipitch` realm, go to "Clients"
2. Click "Create client"
3. Configure the client:
   - **Client ID**: `ipitch-client`
   - **Client Protocol**: `openid-connect`
   - Click "Next"
4. Configure capabilities:
   - Enable "Client authentication"
   - Enable "Authorization"
   - Enable "Standard flow"
   - Enable "Direct access grants"
   - Click "Next"
5. Configure access settings:
   - **Valid redirect URIs**: `http://localhost:*`
   - **Web origins**: `http://localhost:*`
   - Click "Save"

### 3. Create Test Users

1. Go to "Users" in the left menu
2. Click "Add user"
3. Fill in user details:
   - **Username**: testuser
   - **Email**: test@example.com
   - **First Name**: Test
   - **Last Name**: User
   - **Email Verified**: On
   - Click "Create"
4. Set password:
   - Go to "Credentials" tab
   - Click "Set password"
   - Enter password (e.g., `test123`)
   - Turn off "Temporary"
   - Click "Save"

### 4. Configure Roles (Optional)

1. Go to "Realm roles"
2. Create roles as needed (e.g., `user`, `admin`, `contributor`)
3. Assign roles to users in the user's "Role mapping" tab

## User Profile API

The User Manager service provides APIs for managing user profiles.

### User Entity Structure

```json
{
  "userId": "keycloak-user-id",
  "userName": "username",
  "email": "user@example.com",
  "description": "User bio/description",
  "avatarUrl": "https://example.com/avatar.jpg",
  "scores": {
    "interests": ["AI", "Machine Learning"],
    "maturity": 5
  },
  "status": "ACTIVE",
  "viewPermissions": {
    "showEmail": true,
    "showScores": true,
    "showDescription": true
  },
  "createdAt": "2025-01-01T00:00:00",
  "updatedAt": "2025-01-01T00:00:00"
}
```

### API Endpoints

#### Get Own Profile (Full Info)
```bash
GET /api/v1/profile/me
Authorization: Bearer <jwt_token>
```

Returns the full profile including all private information for the authenticated user.

#### Get User Profile by ID (Filtered)
```bash
GET /api/v1/profile/{userId}
Authorization: Bearer <jwt_token>
```

Returns a filtered profile based on the user's privacy settings. If requesting your own profile, returns full information.

#### Update Own Profile
```bash
PUT /api/v1/profile/me
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "description": "Updated bio",
  "avatarUrl": "https://example.com/new-avatar.jpg",
  "viewPermissions": {
    "showEmail": false,
    "showScores": true,
    "showDescription": true
  }
}
```

Only the following fields are editable:
- `description`
- `avatarUrl`
- `viewPermissions`

Other fields (name, email, scores, status) are managed by the system.

## Getting Access Tokens

### Using Direct Access Grant

```bash
curl -X POST http://localhost:8080/realms/ipitch/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ipitch-client" \
  -d "username=testuser" \
  -d "password=test123" \
  -d "grant_type=password"
```

Response:
```json
{
  "access_token": "eyJhbGci...",
  "expires_in": 300,
  "refresh_token": "eyJhbGci...",
  "token_type": "Bearer"
}
```

### Using the Access Token

Include the access token in the Authorization header for all API requests:

```bash
curl -X GET http://localhost:8084/api/v1/profile/me \
  -H "Authorization: Bearer eyJhbGci..."
```

## Environment Variables

All services support the following environment variables for Keycloak configuration:

- `KEYCLOAK_URL`: Keycloak server URL (default: http://localhost:8080)
- `KEYCLOAK_REALM`: Keycloak realm name (default: ipitch)
- `KEYCLOAK_CLIENT_ID`: Client ID (default: ipitch-client)
- `KEYCLOAK_ISSUER_URI`: JWT issuer URI (default: http://localhost:8080/realms/ipitch)
- `KEYCLOAK_JWK_SET_URI`: JWK set URI (default: http://localhost:8080/realms/ipitch/protocol/openid-connect/certs)

## Service Ports

- Keycloak: 8080
- Proposal Manager: 8081
- Proposal View Manager: 8082
- Interaction Manager: 8083
- User Manager: 8084

## Swagger Documentation

Each service provides Swagger UI for API documentation:

- User Manager: http://localhost:8084/swagger-ui.html
- Proposal Manager: http://localhost:8081/swagger-ui.html
- Interaction Manager: http://localhost:8083/swagger-ui.html
- Proposal View Manager: http://localhost:8082/swagger-ui.html

Note: You'll need to configure authentication in Swagger UI to test protected endpoints.

## Troubleshooting

### Keycloak not starting
- Check that port 8080 is not in use
- Ensure the keycloak-postgres container is healthy
- Check logs: `docker logs ipitch-keycloak`

### Authentication errors
- Verify the realm name is `ipitch`
- Ensure the client ID is correctly configured
- Check that the user exists and credentials are correct
- Verify JWT issuer URI matches your Keycloak configuration

### User not found in database
- The user-manager will automatically sync users from Keycloak on first access
- Ensure the Keycloak admin credentials are correct in application.yml
- Check the user-manager logs for sync errors

## Security Notes

- The default admin password (`admin123`) should be changed in production
- Use HTTPS for all production deployments
- Configure proper CORS and redirect URIs for production
- Regularly rotate client secrets
- Enable email verification for production users
