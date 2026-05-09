FROM mcr.microsoft.com/playwright:v1.44.0-jammy
WORKDIR /app
COPY package*.json ./
RUN npm ci
RUN npx playwright install chromium --with-deps
COPY src ./src
CMD ["node", "src/index.js"]
