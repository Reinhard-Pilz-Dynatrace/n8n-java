# E-Commerce Workshop Demo

A demonstration application showing common performance problems for Dynatrace monitoring workshops:
- **Connection leaks** - Unclosed database connections leading to pool exhaustion
- **N+1 queries** - Inefficient database access patterns

## Prerequisites

**Java 17 or higher** is required. Maven is **not required** thanks to Maven Wrapper.

### Installing Java

If you don't have Java installed:

**Windows:**
- Download and install from [Adoptium](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
- Or use [Chocolatey](https://chocolatey.org/): `choco install temurin17`

**macOS:**
- Use [Homebrew](https://brew.sh/): `brew install openjdk@17`
- Or use [SDKMAN!](https://sdkman.io/): `sdk install java 17.0.9-tem`

**Linux:**
```bash
# Debian/Ubuntu
sudo apt update && sudo apt install openjdk-17-jdk

# Red Hat/Fedora
sudo dnf install java-17-openjdk-devel

# Or use SDKMAN!
curl -s "https://get.sdkman.io" | bash
sdk install java 17.0.9-tem
```

Verify installation:
```bash
java -version
```

## Quick Start

### Option 1: Run with Maven Wrapper (Recommended)

**Windows:**
```cmd
mvnw.cmd clean compile exec:java
```

**macOS/Linux:**
```bash
./mvnw clean compile exec:java
```

The application will:
1. Start HTTP server on port 8080
2. Initialize H2 in-memory database with test data
3. Begin load generation (1 request every 5 seconds)

### Option 2: Build and Run Fat JAR

**Build:**
```bash
# Windows
mvnw.cmd clean package

# macOS/Linux
./mvnw clean package
```

**Run:**
```bash
java -jar target/ecommerce-workshop.jar
```

### Option 3: Run with IDE

Import as Maven project in your IDE (IntelliJ IDEA, Eclipse, VS Code) and run:
```
com.dynatrace.ecommerce.WorkshopDemo
```

## Stopping the Application

**Option 1: HTTP Endpoint**
```bash
curl -X POST http://localhost:8080/shutdown
```

**Option 2: Keyboard**
Press `Ctrl+C` in the terminal

## API Endpoints

- `GET /orders?customerId=<id>` - Retrieve orders for a customer
- `GET /health` - Health check endpoint
- `POST /shutdown` - Gracefully shutdown the application

Example:
```bash
curl "http://localhost:8080/orders?customerId=1"
curl "http://localhost:8080/health"
```

## Project Structure

```
src/main/java/com/dynatrace/ecommerce/
├── Order.java                      # Order domain model
├── OrderItem.java                  # Order item model
├── OrderService.java               # Core service (contains bugs!)
├── ShippingInfo.java               # Shipping details model
├── WorkshopDemo.java               # Main entry point
├── client/
│   ├── OrderServiceLoadGenerator.java  # Load generator
│   └── SimpleHttpClient.java           # Raw HTTP client
├── db/
│   ├── DatabaseInitializer.java    # H2 setup and test data
│   ├── PooledConnection.java       # Connection wrapper
│   └── SimpleConnectionPool.java   # Connection pool (2000 max)
└── server/
    └── OrderServiceHttpServer.java # HTTP REST server
```

## The Problems (Workshop Focus)

### 1. Connection Leak (~5% of requests)
- **Location:** `OrderService.getOrderItemsWithValidation()` and `getShippingInfoDetailed()`
- **Pattern:** Modulo-based business rules trigger buggy code paths
- **Result:** ~25 connections leaked per request (customer 1 has ~500 orders)
- **Timeline:** Pool exhaustion after ~6-7 minutes (2000 connections / 25 per request)

### 2. N+1 Queries
- **Location:** `OrderService.getCustomerOrders()`
- **Pattern:** Initial query + separate query for each order's items + separate query for shipping
- **Result:** ~1001 queries per request (1 + 500×2)
- **Impact:** High database load, slow response times

## Configuration

Edit `pom.xml` or source files to adjust:
- **Connection pool size:** `SimpleConnectionPool.MAX_CONNECTIONS` (default: 2000)
- **Think time:** `OrderServiceLoadGenerator.DEFAULT_THINK_TIME_MS` (default: 5000ms)
- **Customer ID:** `OrderServiceLoadGenerator.CUSTOMER_ID` (default: 1)

## Workshop Scenarios

### Scenario 1: Observe Normal Operations (0-5 minutes)
- Application starts, pool slowly fills
- Response times normal (~100-200ms)
- Database queries steady

### Scenario 2: Pool Pressure Builds (5-10 minutes)
- Connection pool approaching limit
- Response times increasing
- Connection wait times visible

### Scenario 3: Pool Exhaustion (10-12 minutes)
- Pool exhausted (2000/2000 connections)
- Requests timing out
- Application degraded/failing

### Dynatrace Will Show:
- ✓ High database response time
- ✓ N+1 query pattern detected
- ✓ Connection pool pressure
- ✓ Memory leak (unclosed connections)
- ✓ Service degradation timeline

## Building for Different Environments

### Development
```bash
./mvnw clean compile exec:java
```

### Production Fat JAR
```bash
./mvnw clean package
java -jar target/ecommerce-workshop.jar
```

### Docker (Future)
```dockerfile
FROM eclipse-temurin:17-jre
COPY target/ecommerce-workshop.jar /app/app.jar
EXPOSE 8080
CMD ["java", "-jar", "/app/app.jar"]
```

## FAQ

**Q: Do I need to keep the `lib/` folder with H2 JAR?**  
A: No! Maven automatically downloads H2. The `lib/` folder can be deleted if using Maven.

**Q: Why Maven Wrapper instead of requiring Maven?**  
A: Maven Wrapper (`mvnw`/`mvnw.cmd`) downloads Maven automatically. Users only need Java installed.

**Q: Can I run multiple instances?**  
A: No, port 8080 is hardcoded. To run multiple instances, modify `OrderServiceHttpServer.PORT`.

**Q: How do I change the leak rate?**  
A: Edit `OrderService.requiresDetailedShipping()` - change `orderId % 20 == 0` to a different modulo.

**Q: Database is in-memory - data persists?**  
A: No, H2 in-memory database resets on each restart. This is intentional for workshop demos.

## Troubleshooting

**"Error: JAVA_HOME is not defined"**
- Set JAVA_HOME environment variable pointing to your JDK installation
- Or use the full path to java: `/path/to/jdk/bin/java -jar target/ecommerce-workshop.jar`

**"Port 8080 already in use"**
- Another application is using port 8080
- Find and stop it, or change the port in `OrderServiceHttpServer.java`

**"OutOfMemoryError"**
- Increase heap: `java -Xmx2g -jar target/ecommerce-workshop.jar`

**Build fails with compilation errors**
- Ensure Java 17 or higher: `java -version`
- Clean and rebuild: `./mvnw clean compile`

## License

This is a demonstration project for educational purposes.
