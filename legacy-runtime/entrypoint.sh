#!/bin/sh
# Boots the J2EE 1.3.1 RI and deploys Pet Store, following the exact order in
# petstore1.3.1_02/docs/installing.html. The order matters: the setup script has
# to run once against a live server to create resources, then again to deploy.
set -e

STATE=/opt/petstore/.provisioned

start_cloudscape() { "$J2EE_HOME/bin/cloudscape" -start & sleep 5; }
start_j2ee()       { "$J2EE_HOME/bin/j2ee" -verbose & : ; }

wait_for_j2ee() {
  echo "waiting for the J2EE server on :8000 ..."
  i=0
  until curl -sf -o /dev/null "http://localhost:8000/" 2>/dev/null || [ $i -ge 120 ]; do
    i=$((i+1)); sleep 2
  done
  [ $i -lt 120 ] || { echo "J2EE server did not come up"; exit 1; }
  echo "J2EE server is up"
}

start_cloudscape
start_j2ee
wait_for_j2ee

if [ ! -f "$STATE" ]; then
  echo "== first boot: creating JMS queues, DB resources and users =="
  cd /opt/petstore && sh setup.sh
  echo "== deploying the four EARs =="
  cd /opt/petstore && sh setup.sh deploy
  touch "$STATE"
  echo
  echo "  Seed the catalog once with:  curl http://localhost:8000/petstore/populate"
  echo "  Then open:                   http://localhost:8000/petstore/"
  echo
fi

echo "== ready =="
# Keep the container alive on the server process.
wait
