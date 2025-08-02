#!/bin/bash

# Initializes the project environment.
# Replaces the default JWT_SECRET in .env with a secure random value.

ENV_FILE="./.env"
DEFAULT_SECRET="your-very-secure-secret-key-here"

if [ ! -f "$ENV_FILE" ]; then
  echo "Error: .env file not found. Please create it from .env.example first."
  exit 1
fi

if grep -q "JWT_SECRET=$DEFAULT_SECRET" "$ENV_FILE"; then
  echo "Default JWT_SECRET found. Generating a new secure secret..."
  
  NEW_SECRET=$(openssl rand -hex 32)
  
  # Replace default secret (works on Linux and macOS)
  sed -i.bak "s/JWT_SECRET=$DEFAULT_SECRET/JWT_SECRET=$NEW_SECRET/" "$ENV_FILE"
  rm "${ENV_FILE}.bak"

  echo "Successfully replaced JWT_SECRET in .env file."
else
  echo "Custom JWT_SECRET already set. No changes made."
fi

echo "Initialization complete."
