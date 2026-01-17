"""
AWS Lambda Function: Data Processor
Processes data from microservices and performs transformations
"""

import json
import os
import boto3
import logging
from datetime import datetime

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# AWS clients
secretsmanager = boto3.client('secretsmanager')
s3 = boto3.client('s3')

def handler(event, context):
    """
    Lambda handler function
    
    Args:
        event: Event data passed to the function
        context: Lambda runtime information
        
    Returns:
        dict: Response with status code and body
    """
    try:
        logger.info(f"Received event: {json.dumps(event)}")
        
        # Extract parameters from event
        service = event.get('service', 'unknown')
        action = event.get('action', 'process')
        data = event.get('data', {})
        
        # Process based on service type
        result = process_service_request(service, action, data)
        
        return {
            'statusCode': 200,
            'status': 'success',
            'service': service,
            'action': action,
            'result': result,
            'timestamp': datetime.utcnow().isoformat()
        }
        
    except Exception as e:
        logger.error(f"Error processing request: {str(e)}", exc_info=True)
        return {
            'statusCode': 500,
            'status': 'error',
            'error': str(e),
            'timestamp': datetime.utcnow().isoformat()
        }

def process_service_request(service, action, data):
    """
    Process service-specific requests
    
    Args:
        service: Name of the service
        action: Action to perform
        data: Data to process
        
    Returns:
        dict: Processing result
    """
    logger.info(f"Processing {action} for service: {service}")
    
    # Service-specific processing logic
    processors = {
        'sampling': process_sampling,
        'evaluation': process_evaluation,
        'evidence': process_evidence,
        'remediation': process_remediation,
        'jira': process_jira
    }
    
    processor = processors.get(service, process_default)
    return processor(action, data)

def process_sampling(action, data):
    """Process sampling service requests"""
    logger.info("Processing sampling request")
    
    # Simulate data sampling logic
    sampled_data = {
        'sample_id': f"SAMPLE-{datetime.utcnow().strftime('%Y%m%d%H%M%S')}",
        'records_sampled': len(data.get('records', [])),
        'sample_rate': 0.1,
        'status': 'completed'
    }
    
    return sampled_data

def process_evaluation(action, data):
    """Process evaluation service requests"""
    logger.info("Processing evaluation request")
    
    # Simulate evaluation logic
    sampling_data = data.get('samplingData', {})
    
    evaluation_result = {
        'evaluation_id': f"EVAL-{datetime.utcnow().strftime('%Y%m%d%H%M%S')}",
        'sample_id': sampling_data.get('sample_id'),
        'score': 85,
        'status': 'pass' if 85 >= 70 else 'fail',
        'findings': []
    }
    
    return evaluation_result

def process_evidence(action, data):
    """Process evidence service requests"""
    logger.info("Processing evidence collection")
    
    evaluation_data = data.get('evaluationData', {})
    
    evidence_result = {
        'evidence_id': f"EVID-{datetime.utcnow().strftime('%Y%m%d%H%M%S')}",
        'evaluation_id': evaluation_data.get('evaluation_id'),
        'artifacts_collected': 5,
        'storage_location': 's3://evidence-bucket/artifacts/',
        'status': 'collected'
    }
    
    return evidence_result

def process_remediation(action, data):
    """Process remediation service requests"""
    logger.info("Processing remediation")
    
    evaluation_data = data.get('evaluationData', {})
    
    remediation_result = {
        'remediation_id': f"REM-{datetime.utcnow().strftime('%Y%m%d%H%M%S')}",
        'evaluation_id': evaluation_data.get('evaluation_id'),
        'actions_taken': ['fix_applied', 'configuration_updated'],
        'status': 'completed'
    }
    
    return remediation_result

def process_jira(action, data):
    """Process JIRA service requests"""
    logger.info("Processing JIRA ticket creation")
    
    evaluation_data = data.get('evaluationData', {})
    
    jira_result = {
        'ticket_id': f"JIRA-{datetime.utcnow().strftime('%Y%m%d')}-001",
        'evaluation_id': evaluation_data.get('evaluation_id'),
        'ticket_url': 'https://jira.example.com/browse/JIRA-001',
        'status': 'created'
    }
    
    return jira_result

def process_default(action, data):
    """Default processor for unknown services"""
    logger.warning(f"Unknown service, using default processor")
    return {
        'status': 'processed',
        'message': 'Default processing applied'
    }

def get_database_credentials():
    """
    Retrieve database credentials from Secrets Manager
    
    Returns:
        dict: Database credentials
    """
    try:
        secret_arn = os.environ.get('DB_SECRET_ARN')
        if not secret_arn:
            logger.warning("DB_SECRET_ARN not configured")
            return None
            
        response = secretsmanager.get_secret_value(SecretId=secret_arn)
        return json.loads(response['SecretString'])
        
    except Exception as e:
        logger.error(f"Error retrieving database credentials: {str(e)}")
        return None
