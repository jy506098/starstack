#!/usr/bin/env bash
# StarStack launcher — builds if needed, then runs the Spring Boot fat JAR
# and opens the default browser to http://localhost:5000/ once the server
# is up.

set -e
cd "$(dirname "$0")"

# Pick the right Maven binary
if command -v mvn >/dev/null 2>&1; then
    MVN=mvn
elif [ -x ./mvnw ]; then
    MVN=./mvnw
else
    echo "[start] mvn / mvnw not found in PATH."
    exit 1
fi

# Build if JAR is missing or older than pom.xml / any source file
NEED_BUILD=0
if [ ! -f target/starstack.jar ]; then
    NEED_BUILD=1
elif [ pom.xml -nt target/starstack.jar ]; then
    NEED_BUILD=1
elif [ -n "$(find src/main/java src/main/resources src/main/typescript -type f \( -name '*.java' -o -name '*.sql' -o -name '*.yml' -o -name '*.yaml' -o -name '*.html' -o -name '*.ts' -o -name '*.tsx' -o -name '*.json' \) -newer target/starstack.jar 2>/dev/null | head -1)" ]; then
    NEED_BUILD=1
fi
if [ "$NEED_BUILD" = "1" ]; then
    echo "[start] Building StarStack JAR..."
    "$MVN" -q -DskipTests package
fi

# Open browser after server has had time to bind (~3-5s)
(
    sleep 4
    URL=http://localhost/
    if command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1
    elif command -v open    >/dev/null 2>&1; then open    "$URL" >/dev/null 2>&1
    elif command -v start   >/dev/null 2>&1; then start   "$URL" >/dev/null 2>&1
    fi
) &

# Run
java -jar target/starstack.jar