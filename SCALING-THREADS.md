# Scaling Consumer Threads

## Current: 3 threads per service
```yaml
spring:
  kafka:
    listener:
      concurrency: 3
```

## For 24 partitions → 5 threads per service
```yaml
spring:
  kafka:
    listener:
      concurrency: 5  # 5 services × 5 threads = 25 total
```

## For 48 partitions → 10 threads per service
```yaml
spring:
  kafka:
    listener:
      concurrency: 10  # 5 services × 10 threads = 50 total
```

## Calculate Thread Count

```
Threads per Service = (Desired Total Threads) / (Number of Services)
```

Example:
- 48 partitions needed
- 5 services available
- Threads per service = 48 / 5 = 9.6 ≈ **10 threads**

This gives 50 total threads for 48 partitions (2 extras as buffer).
