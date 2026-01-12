# Kafka Partition & Consumer Parallelism - Visual Guide

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                    KAFKA TOPIC: user-created-events                 │
│                          12 PARTITIONS                              │
├────────┬────────┬────────┬────────┬────────┬────────┬──────────────┤
│ Part 0 │ Part 1 │ Part 2 │ Part 3 │ Part 4 │ Part 5 │ ... Part 11  │
└────┬───┴───┬────┴───┬────┴───┬────┴───┬────┴───┬────┴──────┬───────┘
     │       │        │        │        │        │           │
     └───────┼────────┼────────┼────────┼────────┼───────────┘
             │        │        │        │        │
             │        │        │        │        │
    ┌────────┴────────┴────────┴────────┴────────┴────────────┐
    │         CONSUMER GROUP: email-service-group              │
    │              15 Consumer Threads                         │
    └──────────────────────────────────────────────────────────┘
             │        │        │        │        │
     ┌───────┼────────┼────────┼────────┼────────┼───────┐
     │       │        │        │        │        │       │
     ▼       ▼        ▼        ▼        ▼        ▼       ▼
┌─────────────────────────────────────────────────────────────┐
│  Service 1     Service 2     Service 3     Service 4  S5    │
│  (Port 8081)   (Port 8082)   (Port 8083)   (Port 8084)(8085)│
│  ┌─┬─┬─┐       ┌─┬─┬─┐       ┌─┬─┬─┐       ┌─┬─┬─┐  ┌─┬─┬─┐│
│  │T│T│T│       │T│T│T│       │T│T│T│       │T│T│T│  │T│T│T││
│  │1│2│3│       │1│2│3│       │1│2│3│       │1│2│3│  │1│2│3││
│  └─┴─┴─┘       └─┴─┴─┘       └─┴─┴─┘       └─┴─┴─┘  └─┴─┴─┘│
│   Active        Active        Active        Active   Standby │
└─────────────────────────────────────────────────────────────┘
     │              │             │              │         │
     ▼              ▼             ▼              ▼         ▼
┌─────────────────────────────────────────────────────────────┐
│              Email Service (Console Output)                  │
│  📧 "Email sent to user1@example.com"                       │
│  📧 "Email sent to user2@example.com"                       │
└─────────────────────────────────────────────────────────────┘
```

## Partition Assignment Details

**⚠️ IMPORTANT: Assignment is DYNAMIC - Not Fixed!**

Kafka's **Consumer Group Coordinator** automatically assigns partitions using the **RangeAssignor** strategy (by default). The assignment shown below is just an example - actual assignment changes each restart based on consumer join order.

**How Kafka Decides Assignment:**
- **Strategy**: RangeAssignor (default) - divides partitions evenly, sorted by partition ID
- **Algorithm**: Sorts partitions and consumers, assigns ranges to each consumer
- **Dynamic**: Assignment recalculated when services start/stop/crash
- **Result**: 12 partitions ÷ 15 consumers = 12 get 1 partition each, 3 idle

**To View Actual Current Assignment:**
```bash
cd C:\kafka
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --describe
```

**Example Assignment (May Differ Each Restart):**
```
┌──────────────────────────────────────────────────────────────┐
│                    12 PARTITIONS                             │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  Partition 0  ──→  Evaluation Service, Thread 1             │
│  Partition 1  ──→  Evaluation Service, Thread 2             │
│  Partition 2  ──→  Evaluation Service, Thread 3             │
│                                                              │
│  Partition 3  ──→  Sampling Service, Thread 1               │
│  Partition 4  ──→  Sampling Service, Thread 2               │
│  Partition 5  ──→  Sampling Service, Thread 3               │
│                                                              │
│  Partition 6  ──→  Evidence Service, Thread 1               │
│  Partition 7  ──→  Evidence Service, Thread 2               │
│  Partition 8  ──→  Evidence Service, Thread 3               │
│                                                              │
│  Partition 9  ──→  Remediation Service, Thread 1            │
│  Partition 10 ──→  Remediation Service, Thread 2            │
│  Partition 11 ──→  Remediation Service, Thread 3            │
│                                                              │
│  (3 threads from Jira Service on standby - ready for        │
│   rebalancing if any service crashes)                       │
└──────────────────────────────────────────────────────────────┘

Note: This is just ONE possible assignment. Your actual assignment 
may look different depending on which services joined first!
```

## Message Flow (Step by Step)

```
STEP 1: User Creation via API
─────────────────────────────────────
POST /api/users
{"username": "alice", "email": "alice@example.com", ...}
           │
           ▼
┌──────────────────────┐
│  UserService         │
│  - Save to H2 DB     │  ✅ User saved
│  - Create Event      │
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  EventPublisher      │
│  @Async              │  🚀 Non-blocking
│  - Serialize Event   │
│  - Calculate Key     │  hash("alice") → Partition
└──────────┬───────────┘
           │
           ▼

STEP 2: Kafka Partitioning
─────────────────────────────────────
           │
    Partition Key = "alice"
    hash("alice") % 12 = 7
           │
           ▼
┌──────────────────────┐
│  Kafka Broker        │
│  Topic: user-        │
│  created-events      │
│                      │
│  [P0][P1][P2][P3]    │  Message goes to
│  [P4][P5][P6][P7]←───┤  Partition 7
│  [P8][P9][P10][P11]  │
└──────────┬───────────┘
           │
           ▼

STEP 3: Consumer Processing
─────────────────────────────────────
           │
    Partition 7 assigned to:
    Evidence Service, Thread 2
           │
           ▼
┌──────────────────────┐
│  @KafkaListener      │
│  Group: email-       │
│  service-group       │
│                      │
│  Thread 2 reads msg  │  📨 Event received
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│  EmailService        │
│  - Format email      │
│  - Print to console  │  📧 EMAIL SENT TO alice@example.com
└──────────┬───────────┘
           │
           ▼
    Offset committed ✅
```

## Load Distribution Example

```
1000 Users Created → How they distribute across 12 partitions

Each user's partition = hash(username) % 12

User "alice"   → hash = 7  → Partition 7  → Evidence Service, T2
User "bob"     → hash = 3  → Partition 3  → Sampling Service, T1
User "carol"   → hash = 11 → Partition 11 → Remediation Service, T3
User "dave"    → hash = 0  → Partition 0  → Evaluation Service, T1
User "eve"     → hash = 5  → Partition 5  → Sampling Service, T3
...

Expected Distribution (roughly even):
┌──────────────────────────────────────────┐
│ Partition 0:  ~83 users ████████         │
│ Partition 1:  ~83 users ████████         │
│ Partition 2:  ~83 users ████████         │
│ Partition 3:  ~83 users ████████         │
│ Partition 4:  ~84 users ████████         │
│ Partition 5:  ~84 users ████████         │
│ Partition 6:  ~84 users ████████         │
│ Partition 7:  ~84 users ████████         │
│ Partition 8:  ~83 users ████████         │
│ Partition 9:  ~83 users ████████         │
│ Partition 10: ~83 users ████████         │
│ Partition 11: ~83 users ████████         │
│                                          │
│ Total: 1000 users ≈ 83-84 per partition │
└──────────────────────────────────────────┘
```

## Scalability Comparison

```
CURRENT SETUP (12 partitions, 15 threads)
═══════════════════════════════════════════
┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐
│ P0 │ P1 │ P2 │ P3 │ P4 │ P5 │ P6 │ P7 │ P8 │ P9 │P10 │P11 │
└─┬──┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┴──┬─┘
  │ T1  │ T2 │ T3 │ T4 │ T5 │ T6 │ T7 │ T8 │ T9 │T10 │T11 │T12│
  └─────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴────┴───┘
  12 threads active, 3 standby
  Throughput: ~1M messages/second


SCALED SETUP (24 partitions, 30 threads)
═══════════════════════════════════════════
┌───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┬───┐
│P0 │P1 │P2 │P3 │P4 │P5 │P6 │P7 │P8 │P9 │P10│P11│...│P20│P21│P22│P23│
└─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬─┴─┬──┘
  │T1 │T2 │T3 │T4 │T5 │T6 │T7 │T8 │T9 │T10│..........................│T24│
  └───┴───┴───┴───┴───┴───┴───┴───┴───┴───┴───────────────────────┴────┘
  24 threads active, 6 standby
  Throughput: ~2-5M messages/second
```

## Rebalancing When Service Fails

```
BEFORE FAILURE (5 services, 12 partitions)
────────────────────────────────────────────
Service1 → [P0, P1, P2]
Service2 → [P3, P4, P5]
Service3 → [P6, P7, P8]     ← Crashes! ❌
Service4 → [P9, P10, P11]
Service5 → [] (standby)


AFTER REBALANCING (4 services, 12 partitions)
────────────────────────────────────────────
Service1 → [P0, P1, P2]
Service2 → [P3, P4, P5]
Service3 → OFFLINE ❌
Service4 → [P9, P10, P11]
Service5 → [P6, P7, P8]     ← Takes over! ✅

⏱️ Rebalancing takes ~5-10 seconds
📨 No messages lost (retained in partitions)
✅ Processing continues automatically
```

## Performance Metrics Visualization

```
Current Load Test Results:
══════════════════════════════════════════════════════════

API Requests:     757 users/sec
                  ████████████████████████████ 757/sec
                  
Kafka Publishing: ~800 events/sec (slightly higher due to async)
                  ████████████████████████████▌ 800/sec
                  
Partition Load:   ~67 msgs/sec per partition (800/12)
                  ████████ 67/sec per partition
                  
Consumer Threads: ~53 msgs/sec per thread (800/15)
                  ██████ 53/sec per thread
                  
Throughput vs Capacity:
[████████░░░░░░░░░░░░░░░░░░░░░░░░] 10% capacity used
└─────────────────────────────────┘
Current: 757/sec   Max: ~10,000/sec (single machine estimate)
```

## How Partition Assignment Works

```
┌─────────────────────────────────────────────────────────────┐
│  PARTITION ASSIGNMENT PROCESS                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  Step 1: Consumer Group Coordinator detects all consumers  │
│          → Sees 15 consumer threads from 5 services        │
│                                                             │
│  Step 2: Uses Assignment Strategy (RangeAssignor default)  │
│          → Sorts partitions: P0, P1, P2, ... P11          │
│          → Sorts consumers by ID                           │
│                                                             │
│  Step 3: Divides partitions among consumers                │
│          → 12 partitions ÷ 15 consumers = 0.8 each        │
│          → First 12 consumers get 1 partition each        │
│          → Last 3 consumers get 0 (idle/standby)          │
│                                                             │
│  Step 4: Assignment changes on rebalance triggers:         │
│          ✓ Consumer joins (service starts)                 │
│          ✓ Consumer leaves (service stops/crashes)         │
│          ✓ Consumer timeout (heartbeat failure)            │
│          ✓ Partition count changes                         │
│                                                             │
│  Step 5: Rebalancing takes 5-10 seconds                    │
│          → All consumers stop processing                   │
│          → Coordinator recalculates assignment             │
│          → Consumers resume with new partitions            │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Alternative Assignment Strategies:**
- **RangeAssignor** (default): Divides partitions into ranges per consumer
- **RoundRobinAssignor**: Assigns partitions round-robin across all consumers
- **StickyAssignor**: Minimizes partition movement during rebalancing
- **CooperativeStickyAssignor**: Like Sticky but allows incremental rebalancing

**To Change Strategy (if needed):**
```yaml
spring:
  kafka:
    consumer:
      properties:
        partition.assignment.strategy: org.apache.kafka.clients.consumer.RoundRobinAssignor
```

## Key Takeaways

```
┌─────────────────────────────────────────────────────────────┐
│  GOLDEN RULES                                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1️⃣  Partitions = Max Parallelism                          │
│     → 12 partitions = max 12 consumers working in parallel │
│                                                             │
│  2️⃣  More Consumers than Partitions = Wasted Resources     │
│     → 15 threads for 12 partitions = 3 threads idle       │
│                                                             │
│  3️⃣  Same Key → Same Partition → Ordering Guaranteed       │
│     → All "alice" messages go to same partition in order  │
│                                                             │
│  4️⃣  Consumer Group = Shared Work                          │
│     → All services share the load across partitions       │
│                                                             │
│  5️⃣  Rebalancing = Automatic Failover                      │
│     → Service crashes? Others take over its partitions    │
│                                                             │
│  6️⃣  Assignment is DYNAMIC - Not Fixed                     │
│     → Kafka decides automatically using assignment strategy│
│     → Can differ each restart based on join order         │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```
