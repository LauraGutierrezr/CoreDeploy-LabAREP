#!/usr/bin/env bash
#
# Deploys the packaged jar + systemd unit to a running EC2 instance over
# SSH/SCP, then installs it as a managed background service. This does not
# launch or configure the EC2 instance itself (that part is done once, by
# hand, in the AWS console) - it only ships and starts the already-built
# artifact.
#
# Static files (HTML/CSS/JS/images) do NOT need to be copied separately:
# they are packaged inside the jar as classpath resources, so shipping the
# jar is enough.
#
# Usage:
#   ./deploy/deploy.sh <instance-public-ip-or-dns> <path-to-ssh-key.pem> [ssh-user]
#
# Example:
#   ./deploy/deploy.sh 54.221.170.57 ~/keys/lab6-key.pem ec2-user
#
# Requirements before running this script:
#   - `mvn package` has already produced target/lambda-web-framework.jar
#   - the instance's security group allows SSH (22) from your IP and the
#     application port (8081 by default) from wherever you'll test it from
#   - the .pem key file has permissions 600
#
# Nothing here embeds a credential, key, or account id: they are only ever
# passed as command-line arguments / files you keep outside the repo.

set -euo pipefail

HOST="${1:?Usage: $0 <host> <path-to-key.pem> [ssh-user]}"
KEY="${2:?Usage: $0 <host> <path-to-key.pem> [ssh-user]}"
SSH_USER="${3:-ec2-user}"

JAR="target/lambda-web-framework.jar"
SERVICE_FILE="deploy/lambda-web-framework.service"
REMOTE_DIR="/opt/lambda-web-framework"

if [[ ! -f "$JAR" ]]; then
  echo "Missing $JAR - run 'mvn package' first." >&2
  exit 1
fi

SSH="ssh -i $KEY -o StrictHostKeyChecking=accept-new $SSH_USER@$HOST"
SCP="scp -i $KEY -o StrictHostKeyChecking=accept-new"

echo "==> Installing the Java runtime on the instance (if not already present)"
$SSH "java -version 2>/dev/null || sudo dnf install -y java-17-amazon-corretto-headless"

echo "==> Creating remote directories"
$SSH "sudo mkdir -p $REMOTE_DIR /var/log/lambda-web-framework && sudo chown -R $SSH_USER: $REMOTE_DIR /var/log/lambda-web-framework"

echo "==> Copying the jar"
$SCP "$JAR" "$SSH_USER@$HOST:$REMOTE_DIR/lambda-web-framework.jar"

echo "==> Installing the systemd service"
$SCP "$SERVICE_FILE" "$SSH_USER@$HOST:/tmp/lambda-web-framework.service"
$SSH "sudo mv /tmp/lambda-web-framework.service /etc/systemd/system/lambda-web-framework.service && sudo systemctl daemon-reload && sudo systemctl enable --now lambda-web-framework"

echo "==> Verifying from inside the instance"
$SSH "sleep 1 && curl -sf http://127.0.0.1:8081/pi && echo"

echo "==> Done. Open http://$HOST:8081/ in a browser to use the application."
echo "    Logs on the instance: /var/log/lambda-web-framework/server.log"
echo "    Stop the service:     ssh -i $KEY $SSH_USER@$HOST sudo systemctl stop lambda-web-framework"
