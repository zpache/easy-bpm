# easy-bpm 控制台镜像：Node 构建 + Nginx 托管（/api 反代到 server:8090）
# 构建（在仓库根目录执行）：
#   docker build -f console/Dockerfile -t easy-bpm-console:latest .

FROM node:22-alpine AS build
WORKDIR /build
RUN corepack enable
COPY console/package.json console/pnpm-lock.yaml* ./
RUN pnpm install --no-frozen-lockfile
COPY console/ ./
RUN pnpm build

FROM nginx:1.27-alpine
COPY console/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
