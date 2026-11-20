# Campus Register: Render classroom demo with private startup-password fallback.
# If Render has no valid CAMPUS_ADMIN_PASSWORD, a random temporary demo
# password is generated on startup and shown only in the service's private logs.
# For actual users/data, configure a persistent secret and persistent database.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY campus-register.jar /app/campus-register.jar
COPY lib/ /app/lib/
RUN mkdir -p /app/data && chown -R 10001:10001 /app
USER 10001
ENV CAMPUS_DATA_DIR=/app/data
ENV CAMPUS_PUBLIC_MODE=true
EXPOSE 10000
CMD ["sh","-c","if [ ${#CAMPUS_ADMIN_PASSWORD} -lt 10 ] || [ ${#CAMPUS_ADMIN_PASSWORD} -gt 128 ]; then\n  CAMPUS_ADMIN_PASSWORD=\"$(tr -d '-' < /proc/sys/kernel/random/uuid)\"\n  export CAMPUS_ADMIN_PASSWORD\n  printf '\\nTEMPORARY DEMO LOGIN PASSWORD (KEEP PRIVATE): %s\\n' \"$CAMPUS_ADMIN_PASSWORD\"\n  echo 'This password is for disposable, fictional-data demonstrations only.'\nfi\nexec java -jar /app/campus-register.jar"]
