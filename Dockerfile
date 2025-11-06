FROM cgr.dev/chainguard/jre:latest
ENV TZ="Europe/Oslo"
ENV JAVA_TOOL_OPTIONS="-XX:+UseParallelGC -XX:MaxRAMPercentage=75"
WORKDIR /app
COPY build/install/fia-dokument-publisering/ /app/
ENTRYPOINT ["java", "-cp", "/app/lib/*", "no.nav.fia.dokument.publisering.ApplicationKt"]