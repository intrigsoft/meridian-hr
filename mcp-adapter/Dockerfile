# syntax=docker/dockerfile:1
# ---- Build stage: compile the TypeScript adapter ----
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json tsconfig.json ./
RUN npm ci
COPY src ./src
RUN npm run build

# ---- Run stage: production deps only, non-root ----
FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force
COPY --from=build /app/dist ./dist
USER node
# PORT and MERIDIAN_BASE_URL are provided by the platform environment.
CMD ["node", "dist/http-server.js"]
