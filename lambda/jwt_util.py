"""
JWT Token Utility for Spring Boot Microservices
Handles JWT generation, validation, and parsing
"""

import jwt
import os
from datetime import datetime, timedelta
from typing import Dict, Optional

class JWTTokenUtil:
    """JWT token utility for Lambda functions"""
    
    def __init__(self):
        # In production, get from AWS Secrets Manager
        self.secret_key = os.environ.get('JWT_SECRET_KEY', 'your-secret-key-change-in-production')
        self.algorithm = 'HS256'
        self.token_expiry_hours = 24
    
    def generate_token(self, username: str, roles: list, claims: dict = None) -> str:
        """
        Generate JWT token with user information
        
        Args:
            username: User's username
            roles: List of user roles
            claims: Additional claims to include
            
        Returns:
            JWT token string
        """
        payload = {
            'sub': username,
            'roles': roles,
            'iat': datetime.utcnow(),
            'exp': datetime.utcnow() + timedelta(hours=self.token_expiry_hours)
        }
        
        if claims:
            payload.update(claims)
        
        token = jwt.encode(payload, self.secret_key, algorithm=self.algorithm)
        return token
    
    def validate_token(self, token: str) -> Dict:
        """
        Validate JWT token and return payload
        
        Args:
            token: JWT token string
            
        Returns:
            Token payload if valid
            
        Raises:
            jwt.ExpiredSignatureError: Token has expired
            jwt.InvalidTokenError: Token is invalid
        """
        try:
            payload = jwt.decode(token, self.secret_key, algorithms=[self.algorithm])
            return payload
        except jwt.ExpiredSignatureError:
            raise Exception('Token has expired')
        except jwt.InvalidTokenError:
            raise Exception('Invalid token')
    
    def get_username_from_token(self, token: str) -> str:
        """Extract username from token"""
        payload = self.validate_token(token)
        return payload.get('sub')
    
    def get_roles_from_token(self, token: str) -> list:
        """Extract roles from token"""
        payload = self.validate_token(token)
        return payload.get('roles', [])
    
    def has_role(self, token: str, required_role: str) -> bool:
        """Check if token has specific role"""
        roles = self.get_roles_from_token(token)
        return required_role in roles

# Example usage in Lambda
def lambda_handler_with_auth(event, context):
    """Example Lambda handler with JWT authentication"""
    
    jwt_util = JWTTokenUtil()
    
    # Extract token from Authorization header
    headers = event.get('headers', {})
    auth_header = headers.get('Authorization', '')
    
    if not auth_header.startswith('Bearer '):
        return {
            'statusCode': 401,
            'body': 'Missing or invalid Authorization header'
        }
    
    token = auth_header.replace('Bearer ', '')
    
    try:
        # Validate token
        payload = jwt_util.validate_token(token)
        username = payload.get('sub')
        roles = payload.get('roles', [])
        
        # Check authorization
        if 'ADMIN' not in roles:
            return {
                'statusCode': 403,
                'body': 'Insufficient permissions'
            }
        
        # Process request
        return {
            'statusCode': 200,
            'body': f'Hello {username}, you have admin access!'
        }
        
    except Exception as e:
        return {
            'statusCode': 401,
            'body': f'Authentication failed: {str(e)}'
        }
