# Task 4 deployment. This uses the runnable release packaged in this repository.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY campus-register.jar /app/campus-register.jar
COPY lib/ /app/lib/
RUN mkdir -p /app/data && chown -R 10001:10001 /app
USER 10001
ENV CAMPUS_DATA_DIR=/app/data
ENV CAMPUS_PUBLIC_MODE=true
EXPOSE 10000
CMD ["java", "-jar", "/app/campus-register.jar"]
