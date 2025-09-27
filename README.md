Digital Wallet Transaction System
Overview
Build a simple digital wallet system demonstrating PostgreSQL and Kafka integration. Users create wallets, add funds, and transfer money. Transaction history uses Kafka events for eventual consistency.
What You'll Learn
	•	PostgreSQL transactions
	•	Kafka producer/consumer patterns
	•	Eventual consistency
Setup
Two Spring Boot apps sharing one PostgreSQL database. Use existing local Kafka in Docker. Dockerize Spring Boot apps.
Architecture
text

Wallet Service (Spring Boot) --> Kafka (wallet_events) --> History Service (Spring Boot)
                              Shared PostgreSQL Database


Flow
	1	Client requests Wallet Service API.
	2	Wallet Service updates database immediately.
	3	Wallet Service publishes event to Kafka.
	4	History Service consumes event and updates history.
	5	Client queries history from History Service.
Process Flow
How Money Moves
text

Client Request
--> Wallet Service --> PostgreSQL
    [wallets: balance: $100, version: 2]
    [wallet_transactions: type: FUND, amount: $50]
--> Response (success)
--> Publish Event
--> Kafka
--> History Service
    [Event: {type: "FUNDED", transaction_events: {amount: 50, event_type: "WALLET_FUNDED", wallet_id: "abc-123", amount: "$50", event_data: {...}, created_at: timestamp}}]


Example: Transfer Transaction
Before Transfer:
	•	Wallet A: Balance $100
	•	Wallet B: Balance $50
During Transfer ($30 from A to B):
	1	Lock both wallets (ORDER BY id to prevent deadlock).
	2	Check A has >= $30.
	3	A.balance -= 30, B.balance += 30.
	4	Record both transactions.
	5	Publish event.
	6	Commit.
After Transfer:
	•	Wallet A: Balance $70
	•	Wallet B: Balance $80 Eventually:
	•	History Service: 2 events (A sent $30, B recv $30)
	•	Kafka Event: TRANSFER_DONE
Services
Wallet Service
	•	Create wallets for users.
	•	Add funds to wallets.
	•	Transfer money between wallets.
	•	Publish events.
History Service
	•	Listen for events from Kafka.
	•	Store transaction history.
	•	Provide history APIs.
	•	Handle duplicates gracefully.
Database Design
One PostgreSQL database with three tables:
sql

-- Wallet Service owns these
CREATE TABLE wallets (
  id VARCHAR(36) PRIMARY KEY,
  user_id VARCHAR(100) NOT NULL,
  balance DECIMAL(19,4) NOT NULL DEFAULT 0,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wallet_transactions (
  id VARCHAR(36) PRIMARY KEY,
  wallet_id VARCHAR(36) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  type VARCHAR(20) NOT NULL,  -- 'FUND', 'TRANSFER_OUT', 'TRANSFER_IN'
  status VARCHAR(20) NOT NULL,  -- 'COMPLETED', 'FAILED'
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (wallet_id) REFERENCES wallets(id)
);

-- History Service owns this
CREATE TABLE transaction_events (
  id VARCHAR(36) PRIMARY KEY,
  wallet_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(100) NOT NULL,
  amount DECIMAL(19,4) NOT NULL,
  event_type VARCHAR(30) NOT NULL,
  transaction_id VARCHAR(36),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  event_data JSONB
);


API Endpoints
Wallet Service
	•	POST /wallets - Create wallet for userId.
	•	POST /wallets/{walletId}/fund - Add money.
	•	POST /wallets/{walletId}/transfer - Send money to another wallet.
	•	GET /wallets/{walletId} - Check balance.
	•	GET /users/{userId}/wallets - List user's wallets.
History Service
	•	GET /wallets/{walletId}/history - Transaction history.
	•	GET /users/{userId}/activity - All user activity.
Core Operations
	1	Create wallet: Insert into PostgreSQL + publish event.
	2	Fund wallet: Update balance with optimistic locking + publish event.
	3	Transfer money: Update two wallets + publish event.
	4	Consume events: Process Kafka events into history table.
	5	Query history: Read from event-sourced history.
Key Learning Scenarios
PostgreSQL
	•	Handle concurrent updates without races.
	•	Use DECIMAL for money (no floats).
	•	Manage DB + Kafka transactions.
	•	Optimistic locking via version fields.
Kafka
	•	Producers handle failures.
	•	Proper consumer groups.
	•	Handle duplicates.
	•	Robust event schemas.
Integration
	•	Eventual consistency.
	•	Handle Kafka downtime with PostgreSQL up.
	•	Retry failures.
	•	Correlate events.
Business Rules
	•	Users: String IDs (no auth).
	•	Balances non-negative.
	•	Money: 4 decimal places.
	•	Balance updates immediate.
	•	History eventual.
	•	All auditable.
Event Design
Events:
json

{
  "eventType": "WALLET_FUNDED",
  "walletId": "wallet-123",
  "userId": "user-456",
  "amount": "100.00",
  "transactionId": "txn-789",
  "timestamp": "2025-08-19T10:30:00Z"
}


Types:
	•	WALLET_CREATED
	•	WALLET_FUNDED
	•	TRANSFER_COMPLETED
	•	TRANSFER_FAILED
Tech Stack
	•	Backend: Java + Spring Boot
	•	Database: PostgreSQL + Spring Data JPA
	•	Messaging: Apache Kafka
	•	Infrastructure: Docker Compose
	•	Testing: Integration tests spanning services
Success Criteria
	•	Concurrent funding without loss.
	•	Reliable event flow.
	•	History catches up on restarts.
	•	Understand optimistic locking for finance.
	•	Explain eventual consistency trade-offs.
	•	Goal: Understand PostgreSQL-Kafka in distributed systems, not production code.
Implementation Guide
Step 1: Project Setup
Create two Maven projects: wallet-service and history-service.
	•	Use Spring Initializr: Java 17+, Spring Boot 3.3+.
	•	Dependencies (both): Spring Web, Spring Data JPA, PostgreSQL Driver, Lombok.
	•	Wallet: Add Spring Kafka.
	•	History: Add Spring Kafka.
Step 2: Docker Setup
Use Docker Compose for apps, PostgreSQL; link to existing Kafka.
docker-compose.yml:
yaml

version: '3'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: walletdb
      POSTGRES_USER: user
      POSTGRES_PASSWORD: pass
    ports:
      - "5432:5432"
  wallet-service:
    build: ./wallet-service
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/walletdb
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: pass
      SPRING_KAFKA_BOOTSTRAP_SERVERS: your-kafka-host:9092  # Link to local Kafka
    ports:
      - "8080:8080"
  history-service:
    build: ./history-service
    depends_on:
      - postgres
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/walletdb
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: pass
      SPRING_KAFKA_BOOTSTRAP_SERVERS: your-kafka-host:9092
    ports:
      - "8081:8081"


For each service, add Dockerfile:
dockerfile

FROM openjdk:17-jdk-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]


Build: mvn clean package, then docker-compose up.
Step 3: Database Entities
In both services, create JPA entities matching DB design.
	•	Wallets: @Entity, @Id String id = UUID.randomUUID().toString(), BigDecimal balance, long version.
	•	WalletTransactions: Similar.
	•	TransactionEvents: In History only.
Use @Version on version field for optimistic locking.
Step 4: Wallet Service
	•	Repositories: JpaRepository for Wallets, WalletTransactions.
	•	Services: WalletService with methods:
	◦	createWallet(String userId): Insert Wallet, publish WALLET_CREATED.
	◦	fundWallet(String walletId, BigDecimal amount): Find wallet, check version, update balance/version, insert transaction (FUND, COMPLETED), publish WALLET_FUNDED. Use @Transactional.
	◦	transfer(String fromId, String toId, BigDecimal amount): @Transactional, lock both (SELECT FOR UPDATE ORDER BY id), check from balance, update both balances/versions, insert two transactions (TRANSFER_OUT, TRANSFER_IN), publish TRANSFER_COMPLETED.
	•	Controllers: REST for APIs.
	•	Kafka: KafkaTemplate<String, String> for publishing JSON events to "wallet_events". Use ObjectMapper for serialization. Enable idempotence: producer.properties acks=all, enable.idempotence=true, retries=Integer.MAX_VALUE.
Step 5: History Service
	•	Repository: JpaRepository for TransactionEvents.
	•	Kafka Listener: @KafkaListener(topics = "wallet_events", groupId = "history-group").
	•	Consume: Parse JSON, check for duplicates (idempotency via id or transaction_id), insert TransactionEvents.
	•	Enable at-least-once: auto.offset.reset=earliest, enable.auto.commit=false, ack after insert.
	•	Controllers: REST for history queries (findByWalletId, findByUserId).
Step 6: Robustness
	•	Optimistic locking: Handle OptimisticLockException, retry or fail.
	•	Duplicates: History checks if event id exists before insert.
	•	Failures: Producers retry on transient errors. Consumers manual ack after success.
	•	Kafka down: Wallet continues DB updates; events queue when up.
	•	Testing: Integration tests with Testcontainers for PostgreSQL/Kafka, or embedded. Test concurrent funds/transfers.
Step 7: Run and Test
	•	Run Docker Compose.
	•	Use Postman for APIs.
	•	Simulate concurrency with JMeter.
	•	Restart services to check eventual consistency.
