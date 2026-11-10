# easy-bpm 服务端镜像
# 前置：先在宿主机构建 jar（JDK 25）：
#   cd server && JAVA_HOME=<corretto-25> mvn -DskipTests package
# 再在仓库根目录执行：
#   docker build -f server/Dockerfile -t easy-bpm-server:latest .

FROM amazoncorretto:25

WORKDIR /app

COPY server/target/easy-bpm-server-*.jar app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

EXPOSE 8090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
