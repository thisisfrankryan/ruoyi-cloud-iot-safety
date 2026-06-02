# =========================================================================
# 首席架构师云原生调优 Dockerfile (针对 ruoyi-modules-iot 模块)
# =========================================================================

# 使用轻量且长期支持安全更新的 eclipse-temurin 17 JRE Alpine 镜像作为运行底座
FROM eclipse-temurin:17-jre-alpine

# 设置工作目录
WORKDIR /app

# 设置时区为亚洲/上海，确保容器时间与北京时间完全同步，报文时间戳准确无误
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 复制经过 Maven 编译打包好的微服务 jar 包到容器中
COPY target/ruoyi-modules-iot.jar /app/ruoyi-modules-iot.jar

# =========================================================================
# 核心亮点：JVM 垃圾回收优化参数调校 (抗洪防抖设计)
# 1. -XX:+UseG1GC: 启用现代 G1 垃圾回收器，在大数据吞吐时能取得优异且可控的停顿时间。
# 2. -XX:MaxGCPauseMillis=100: 将最大 GC 暂停时间严格压缩在 100 毫秒以内，保障救命报文闪烁不卡死。
# 3. -XX:InitiatingHeapOccupancyPercent=45: 当堆内存占用率达到 45% 时即触发并发标记，提早大扫除避免 Full GC。
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
  -Djava.security.egd=file:/dev/./urouter \
  -Dfile.encoding=UTF-8"

# 暴露若依微服务专有端口
EXPOSE 8080

# 优雅启动微服务
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar ruoyi-modules-iot.jar"]
