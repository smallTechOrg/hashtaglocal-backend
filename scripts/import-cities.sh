#!/bin/bash

# Script to import Indian city polygons into the Localities table
# Usage: ./import-cities.sh

echo "================================================"
echo "Indian City Polygon Import Script"
echo "================================================"
echo ""
echo "This will import 100+ Indian cities with their"
echo "geographic boundaries into the Localities table."
echo ""
echo "Estimated time: 2-3 minutes"
echo "Data source: OpenStreetMap via Nominatim API"
echo ""
echo "Press Ctrl+C to cancel, or wait 5 seconds to continue..."
echo ""

sleep 5

echo "Starting import..."
echo ""

./gradlew bootRun --args='--import-cities=true'

echo ""
echo "================================================"
echo "Import process completed!"
echo "Check the logs above for details."
echo "================================================"
