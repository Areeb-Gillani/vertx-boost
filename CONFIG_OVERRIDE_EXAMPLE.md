# Configuration Override Examples

This document demonstrates how to override configuration values using command-line arguments and environment variables in vertx-boost.

## Precedence Order

Configuration values are resolved in the following order (highest to lowest priority):

1. **Command-line arguments** (via `-D` flags)
2. **Environment variables**
3. **Config file** (config.json)

## Hierarchical Property Notation

Both command-line arguments and environment variables support dot notation to create nested JSON structures.

### Example: Basic Override

**Config file (config.json):**
```json
{
  "server": {
    "http": {
      "port": 8080,
      "enable": true
    }
  },
  "eventLoopPoolSize": 5
}
```

**Command-line override:**
```bash
java -Dserver.http.port=9090 -DeventLoopPoolSize=10 -jar your-app.jar
```

**Resulting configuration:**
```json
{
  "server": {
    "http": {
      "port": 9090,
      "enable": true
    }
  },
  "eventLoopPoolSize": 10
}
```

### Example: Creating New Nested Properties

**Command-line:**
```bash
java -Dapp.feature.enabled=true -Dapp.feature.timeout=5000 -jar your-app.jar
```

**Resulting configuration:**
```json
{
  "server": {
    "http": {
      "port": 8080,
      "enable": true
    }
  },
  "app": {
    "feature": {
      "enabled": true,
      "timeout": 5000
    }
  }
}
```

### Example: Environment Variables

**Set environment variables:**
```bash
# Windows PowerShell
$env:server.http.port = "9090"
$env:database.host = "localhost"

# Linux/Mac
export server.http.port=9090
export database.host=localhost
```

**Run application:**
```bash
java -jar your-app.jar
```

The environment variables will override the config file values.

### Example: Combined Override

**Environment variables:**
```bash
export server.http.port=9090
```

**Command-line:**
```bash
java -Dserver.http.port=8888 -jar your-app.jar
```

**Result:** Port will be **8888** (command-line takes precedence over environment variable)

## Type Conversion

The framework automatically converts string values to appropriate types:

- `"true"` / `"false"` → Boolean
- `"123"` → Integer
- `"123.45"` → Double
- Other values → String

**Example:**
```bash
java -Dserver.http.enable=false -Dserver.http.port=9090 -jar your-app.jar
```

Results in:
```json
{
  "server": {
    "http": {
      "enable": false,
      "port": 9090
    }
  }
}
```

## Usage in BoostApplication

Your main class remains unchanged:

```java
public class Main extends BoostApplication {
    public static void main(String[] args) {
        run(Main.class, args, "config.json", false);
    }
}
```

The configuration overrides are automatically applied during the initialization phase.

## Logging

When overrides are applied, you'll see log messages indicating which properties were overridden:

```
Config override applied from System Property: server.http.port = 9090
Config override applied from Environment Variable: database.host = localhost
```

## Best Practices

1. **Use dot notation consistently**: `a.b.c` for nested properties
2. **Environment variables for deployment**: Use env vars for environment-specific config (dev/staging/prod)
3. **Command-line for testing**: Use `-D` flags for quick testing and debugging
4. **Keep config.json for defaults**: Maintain sensible defaults in your config file
5. **Document overridable properties**: List which properties can be overridden in your application documentation
