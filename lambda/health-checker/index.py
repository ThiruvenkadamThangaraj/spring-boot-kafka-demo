"""
AWS Lambda Function: Health Checker
Performs health checks on microservices
"""

import json
import os
import boto3
import logging
import urllib3
from datetime import datetime

# Configure logging
logger = logging.getLogger()
logger.setLevel(logging.INFO)

# HTTP client
http = urllib3.PoolManager()

# AWS clients
sns = boto3.client('sns')
cloudwatch = boto3.client('cloudwatch')

def handler(event, context):
    """
    Lambda handler function for health checks
    
    Args:
        event: Event data passed to the function
        context: Lambda runtime information
        
    Returns:
        dict: Health check results
    """
    try:
        logger.info("Starting health checks")
        
        # Get configuration
        alb_dns = os.environ.get('ALB_DNS_NAME')
        services = json.loads(os.environ.get('SERVICES', '[]'))
        
        if not alb_dns:
            logger.error("ALB_DNS_NAME not configured")
            return {
                'statusCode': 500,
                'body': json.dumps({'error': 'Configuration error'})
            }
        
        # Perform health checks
        results = {}
        all_healthy = True
        
        for service in services:
            health_status = check_service_health(alb_dns, service)
            results[service] = health_status
            
            if not health_status['healthy']:
                all_healthy = False
                send_alert(service, health_status)
            
            # Send metrics to CloudWatch
            send_metrics(service, health_status)
        
        # Summary
        summary = {
            'timestamp': datetime.utcnow().isoformat(),
            'total_services': len(services),
            'healthy_count': sum(1 for r in results.values() if r['healthy']),
            'unhealthy_count': sum(1 for r in results.values() if not r['healthy']),
            'all_healthy': all_healthy,
            'results': results
        }
        
        logger.info(f"Health check summary: {json.dumps(summary)}")
        
        return {
            'statusCode': 200,
            'body': json.dumps(summary)
        }
        
    except Exception as e:
        logger.error(f"Error in health check: {str(e)}", exc_info=True)
        return {
            'statusCode': 500,
            'body': json.dumps({
                'error': str(e),
                'timestamp': datetime.utcnow().isoformat()
            })
        }

def check_service_health(alb_dns, service_name):
    """
    Check health of a specific service
    
    Args:
        alb_dns: ALB DNS name
        service_name: Name of the service to check
        
    Returns:
        dict: Health status
    """
    health_url = f"http://{alb_dns}/{service_name}/actuator/health"
    
    try:
        logger.info(f"Checking health: {health_url}")
        
        response = http.request(
            'GET',
            health_url,
            timeout=5.0,
            retries=False
        )
        
        status_code = response.status
        healthy = status_code == 200
        
        try:
            body = json.loads(response.data.decode('utf-8'))
            status = body.get('status', 'UNKNOWN')
        except:
            body = {}
            status = 'UNKNOWN'
        
        result = {
            'healthy': healthy,
            'status_code': status_code,
            'status': status,
            'response_time_ms': 0,  # Could measure actual response time
            'timestamp': datetime.utcnow().isoformat()
        }
        
        logger.info(f"Service {service_name}: {result}")
        return result
        
    except Exception as e:
        logger.error(f"Health check failed for {service_name}: {str(e)}")
        return {
            'healthy': False,
            'status_code': 0,
            'status': 'ERROR',
            'error': str(e),
            'timestamp': datetime.utcnow().isoformat()
        }

def send_alert(service_name, health_status):
    """
    Send alert notification for unhealthy service
    
    Args:
        service_name: Name of the unhealthy service
        health_status: Health status details
    """
    try:
        topic_arn = os.environ.get('SNS_TOPIC_ARN')
        if not topic_arn:
            logger.warning("SNS_TOPIC_ARN not configured")
            return
        
        message = {
            'service': service_name,
            'status': 'UNHEALTHY',
            'details': health_status,
            'timestamp': datetime.utcnow().isoformat()
        }
        
        sns.publish(
            TopicArn=topic_arn,
            Subject=f"Alert: {service_name} is unhealthy",
            Message=json.dumps(message, indent=2)
        )
        
        logger.info(f"Alert sent for {service_name}")
        
    except Exception as e:
        logger.error(f"Error sending alert: {str(e)}")

def send_metrics(service_name, health_status):
    """
    Send metrics to CloudWatch
    
    Args:
        service_name: Name of the service
        health_status: Health status details
    """
    try:
        environment = os.environ.get('ENVIRONMENT', 'dev')
        
        cloudwatch.put_metric_data(
            Namespace='Microservices/Health',
            MetricData=[
                {
                    'MetricName': 'ServiceHealth',
                    'Value': 1.0 if health_status['healthy'] else 0.0,
                    'Unit': 'None',
                    'Timestamp': datetime.utcnow(),
                    'Dimensions': [
                        {'Name': 'ServiceName', 'Value': service_name},
                        {'Name': 'Environment', 'Value': environment}
                    ]
                },
                {
                    'MetricName': 'ResponseCode',
                    'Value': float(health_status['status_code']),
                    'Unit': 'None',
                    'Timestamp': datetime.utcnow(),
                    'Dimensions': [
                        {'Name': 'ServiceName', 'Value': service_name},
                        {'Name': 'Environment', 'Value': environment}
                    ]
                }
            ]
        )
        
        logger.debug(f"Metrics sent for {service_name}")
        
    except Exception as e:
        logger.error(f"Error sending metrics: {str(e)}")
