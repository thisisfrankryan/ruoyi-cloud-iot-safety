# =========================================================================
# ruoyi-modules-iot Dockerfile
# =========================================================================

# 使用轻量且长期支持安全更新的 eclipse-temurin 17 JRE Alpine 镜像作为运行底座
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 设置时区为亚洲/上海，确保容器时间与业务日志时间一致
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 复制经过 Maven 编译打包好的微服务 jar 包到容器中
COPY target/ruoyi-modules-iot.jar /app/ruoyi-modules-iot.jar

# =========================================================================
# JVM 参数：使用 G1GC 并设置目标停顿时间，适配告警消费类服务。
# 1. -XX:+UseG1GC: 启用 G1 垃圾回收器。
# 2. -XX:MaxGCPauseMillis=100: 设置 GC 目标停顿时间。
# 3. -XX:InitiatingHeapOccupancyPercent=45: 堆内存占用率达到 45% 时触发并发标记。
# 4. -XX:G1ReservePercent=15: 保留 15% 的空闲内存防止晋升失败。
# 5. -Xms512m -Xmx1024m: 合理限制堆大小，配合容器弹性限制。
# =========================================================================
ENV JAVA_OPTS="\
  -server \
  -Xms512m \
  -Xmx1024m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:InitiatingHeapOccupancyPercent=45 \
  -XX:G1ReservePercent=15 \
  -XX:G1HeapRegionSize=16m \
  -Djava.security.egd=file:/dev/./urandom \
  -Dfile.encoding=UTF-8"

# 暴露 IoT 微服务端口
EXPOSE 9800

# 优雅启动微服务
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar ruoyi-modules-iot.jar"]
