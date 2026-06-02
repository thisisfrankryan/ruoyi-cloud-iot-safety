FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone
COPY target/ruoyi-modules-system.jar /app/ruoyi-modules-system.jar
ENV JAVA_OPTS="-server -Xms256m -Xmx512m -XX:+UseG1GC"
EXPOSE 9201
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar ruoyi-modules-system.jar"]
