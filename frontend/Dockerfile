FROM node:18-alpine

WORKDIR /app

# Create a non-root user and group
RUN addgroup -S appgroup && adduser -S -G appgroup appuser

COPY package*.json ./
RUN npm install

COPY . .

# Set ownership for the app directory
RUN chown -R appuser:appgroup /app

# Switch to the non-root user
USER appuser

EXPOSE 3000

CMD ["npm", "start"]
