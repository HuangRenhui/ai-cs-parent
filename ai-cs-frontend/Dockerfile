FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci --registry=https://registry.npmmirror.com
COPY . .
# 默认构建保留演示 mock；正式镜像请传 --build-arg BUILD_SCRIPT=build:release
ARG BUILD_SCRIPT=build
RUN npm run ${BUILD_SCRIPT}

FROM nginx:1.25-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
