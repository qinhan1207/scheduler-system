# 使用轻量级的 OpenJDK 17 镜像作为基础
FROM openjdk:17-jdk-slim

# 设置容器内的工作目录
WORKDIR /app

# 将 Maven 构建好的 JAR 包复制到镜像中
# 注意：这里假设在构建镜像前，您已经在项目根目录执行了 'mvn clean package'
# 并且 jar 包生成在 local-scheduler-agent/target/ 目录下
COPY target/local-scheduler-agent-1.0-SNAPSHOT.jar app.jar

# 暴露 Agent 的监听端口 (与 application.yaml 中的 server.port 对应)
EXPOSE 8081

# 设置容器启动时执行的命令
ENTRYPOINT ["java", "-jar", "app.jar"]